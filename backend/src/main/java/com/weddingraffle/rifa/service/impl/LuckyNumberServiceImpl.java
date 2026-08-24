package com.weddingraffle.rifa.service.impl;

import com.weddingraffle.rifa.config.AppProperties;
import com.weddingraffle.rifa.config.LuckyNumberAllocationProperties;
import com.weddingraffle.rifa.entity.LuckyNumber;
import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.Transaction;
import com.weddingraffle.rifa.exception.LuckyNumberAllocationException;
import com.weddingraffle.rifa.exception.LuckyNumberAllocationException.Reason;
import com.weddingraffle.rifa.repository.LuckyNumberAllocationCandidate;
import com.weddingraffle.rifa.repository.LuckyNumberAllocationRepository;
import com.weddingraffle.rifa.repository.LuckyNumberRepository;
import com.weddingraffle.rifa.service.LuckyNumberCandidateGenerator;
import com.weddingraffle.rifa.service.LuckyNumberService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LuckyNumberServiceImpl implements LuckyNumberService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LuckyNumberServiceImpl.class);
    private static final int RANDOM_DRAW_MULTIPLIER = 4;

    private final AppProperties appProperties;
    private final LuckyNumberAllocationProperties allocationProperties;
    private final LuckyNumberRepository luckyNumberRepository;
    private final LuckyNumberAllocationRepository allocationRepository;
    private final LuckyNumberCandidateGenerator candidateGenerator;
    private final Clock clock;

    public LuckyNumberServiceImpl(
            AppProperties appProperties,
            LuckyNumberAllocationProperties allocationProperties,
            LuckyNumberRepository luckyNumberRepository,
            LuckyNumberAllocationRepository allocationRepository,
            LuckyNumberCandidateGenerator candidateGenerator,
            Clock clock) {
        this.appProperties = appProperties;
        this.allocationProperties = allocationProperties;
        this.luckyNumberRepository = luckyNumberRepository;
        this.allocationRepository = allocationRepository;
        this.candidateGenerator = candidateGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public List<LuckyNumber> generateFor(Transaction transaction) {
        if (transaction.hasCompletedLuckyNumberBatch()) {
            return completedBatch(transaction);
        }
        if (luckyNumberRepository.existsByTransaction(transaction)) {
            throw new IllegalStateException("Lucky numbers exist without a completed batch marker.");
        }

        NumberRange range = numberRange();
        if (transaction.getQuantity() > range.capacity()) {
            throw insufficientNumbers(transaction, transaction.getQuantity(), range.capacity());
        }

        long startedAt = System.nanoTime();
        AllocationProgress progress = allocate(transaction, range);
        int compacted = allocationRepository.compactAllocationIndexes(requireTransactionId(transaction));
        if (compacted != transaction.getQuantity()) {
            throw new IllegalStateException("Lucky-number allocation compaction did not update the exact batch.");
        }

        List<LuckyNumber> persisted = luckyNumberRepository.findByTransactionOrderByNumberAsc(transaction);
        ensureExactBatch(transaction, persisted);
        transaction.markLuckyNumberBatchCompleted(OffsetDateTime.now(clock));
        LOGGER.info(
                "Allocated lucky-number batch transactionId={} quantity={} primaryBatches={} fallbackBatches={} rejectedCandidates={} durationMillis={}",
                transaction.getId(),
                transaction.getQuantity(),
                progress.primaryBatches(),
                progress.fallbackBatches(),
                progress.rejectedCandidates(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        return persisted;
    }

    @Override
    public List<String> findNumbers(String externalReference) {
        return luckyNumberRepository.findNumbersByTransactionExternalReference(externalReference);
    }

    @Override
    public List<String> findApprovedNumbersByPhone(String phone) {
        return luckyNumberRepository.findNumbersByPhoneAndStatus(phone, PaymentStatus.APPROVED);
    }

    @Override
    public List<String> findPreviousApprovedNumbers(String phone, String externalReference) {
        return luckyNumberRepository.findNumbersByPhoneAndStatusExcludingExternalReference(
                phone, PaymentStatus.APPROVED, externalReference);
    }

    private AllocationProgress allocate(Transaction transaction, NumberRange range) {
        long transactionId = requireTransactionId(transaction);
        int quantity = transaction.getQuantity();
        int allocated = 0;
        int primaryBatches = 0;
        int fallbackBatches = 0;
        int rejectedCandidates = 0;
        int conflictRetries = 0;
        // Allocation may leave conflict holes. Temporary indexes above the final 1..quantity range
        // keep retries independent; one set-based update compacts them only after the batch is full.
        int nextTemporaryIndex = temporaryIndexAfter(quantity, 1);
        int initialSetCapacity = Math.min(
                quantity, Math.toIntExact(Math.min(Integer.MAX_VALUE, (long) allocationProperties.chunkSize() * 2)));
        Set<Integer> attemptedCandidates = new HashSet<>(initialSetCapacity);
        boolean fallbackRequired = false;

        while (allocated < quantity) {
            int requested = Math.min(allocationProperties.chunkSize(), quantity - allocated);
            if (!fallbackRequired) {
                List<LuckyNumberAllocationCandidate> candidates =
                        randomCandidates(range, requested, nextTemporaryIndex, attemptedCandidates);
                List<String> inserted =
                        allocationRepository.insertCandidates(transactionId, transaction.getEmail(), candidates);
                primaryBatches++;
                allocated += inserted.size();
                rejectedCandidates += candidates.size() - inserted.size();
                nextTemporaryIndex = temporaryIndexAfter(nextTemporaryIndex, candidates.size());
                fallbackRequired = candidates.size() < requested || inserted.size() < candidates.size();
                if (fallbackRequired) {
                    LOGGER.warn(
                            "Lucky-number allocation switched to set-based fallback transactionId={} requested={} candidates={} inserted={}",
                            transactionId,
                            requested,
                            candidates.size(),
                            inserted.size());
                }
                continue;
            }

            int randomSeed = candidateGenerator.nextInt(range.min(), range.max());
            List<String> inserted = allocationRepository.insertRandomAvailable(
                    transactionId,
                    transaction.getEmail(),
                    range.min(),
                    range.max(),
                    range.width(),
                    requested,
                    nextTemporaryIndex,
                    randomSeed);
            fallbackBatches++;
            allocated += inserted.size();
            rejectedCandidates += requested - inserted.size();
            nextTemporaryIndex = temporaryIndexAfter(nextTemporaryIndex, requested);

            if (inserted.size() < requested) {
                int missing = quantity - allocated;
                long available = allocationRepository.countAvailable(range.min(), range.max(), range.width());
                if (available < missing) {
                    throw insufficientNumbers(transaction, missing, available);
                }
                conflictRetries++;
                LOGGER.warn(
                        "Concurrent lucky-number conflicts require retry transactionId={} requested={} inserted={} retry={}/{}",
                        transactionId,
                        requested,
                        inserted.size(),
                        conflictRetries,
                        allocationProperties.maxConflictRetries());
                if (conflictRetries > allocationProperties.maxConflictRetries()) {
                    throw retryLimitReached(transaction, allocated, quantity, conflictRetries);
                }
            }
        }

        return new AllocationProgress(primaryBatches, fallbackBatches, rejectedCandidates);
    }

    private List<LuckyNumberAllocationCandidate> randomCandidates(
            NumberRange range, int requested, int firstTemporaryIndex, Set<Integer> attemptedCandidates) {
        List<LuckyNumberAllocationCandidate> candidates = new ArrayList<>(requested);
        long maxDraws = Math.max(requested, (long) requested * RANDOM_DRAW_MULTIPLIER);
        for (long draw = 0;
                draw < maxDraws && candidates.size() < requested && attemptedCandidates.size() < range.capacity();
                draw++) {
            int value = candidateGenerator.nextInt(range.min(), range.max());
            if (value < range.min() || value > range.max()) {
                throw new IllegalStateException("Lucky-number candidate generator returned a value outside the range.");
            }
            if (attemptedCandidates.add(value)) {
                candidates.add(new LuckyNumberAllocationCandidate(
                        range.format(value), temporaryIndexAfter(firstTemporaryIndex, candidates.size())));
            }
        }
        return candidates;
    }

    private NumberRange numberRange() {
        String minValue = appProperties.raffle().numberMin();
        String maxValue = appProperties.raffle().numberMax();
        int min = Integer.parseInt(minValue);
        int max = Integer.parseInt(maxValue);
        if (min > max) {
            throw new IllegalStateException("Invalid lucky number range.");
        }
        return new NumberRange(min, max, Math.max(minValue.length(), maxValue.length()));
    }

    private List<LuckyNumber> completedBatch(Transaction transaction) {
        List<LuckyNumber> luckyNumbers = luckyNumberRepository.findByTransactionOrderByNumberAsc(transaction);
        ensureExactBatch(transaction, luckyNumbers);
        return luckyNumbers;
    }

    private LuckyNumberAllocationException insufficientNumbers(Transaction transaction, long missing, long available) {
        LOGGER.error(
                "Lucky-number allocation has insufficient inventory and will roll back transactionId={} missing={} available={}",
                transaction.getId(),
                missing,
                available);
        return new LuckyNumberAllocationException(
                Reason.INSUFFICIENT_NUMBERS, "Not enough lucky numbers are available to complete this transaction.");
    }

    private LuckyNumberAllocationException retryLimitReached(
            Transaction transaction, int allocated, int quantity, int retries) {
        LOGGER.error(
                "Lucky-number allocation retry limit reached and will roll back transactionId={} allocated={} expected={} retries={}",
                transaction.getId(),
                allocated,
                quantity,
                retries);
        return new LuckyNumberAllocationException(
                Reason.RETRY_LIMIT_REACHED,
                "Lucky-number allocation could not be completed after concurrent conflicts.");
    }

    private static long requireTransactionId(Transaction transaction) {
        if (transaction.getId() == null) {
            throw new IllegalStateException("Lucky numbers require a persisted transaction.");
        }
        return transaction.getId();
    }

    private static int temporaryIndexAfter(int firstIndex, int usedIndexes) {
        return Math.toIntExact((long) firstIndex + usedIndexes);
    }

    private static void ensureExactBatch(Transaction transaction, List<LuckyNumber> luckyNumbers) {
        Set<Integer> indexes = new HashSet<>();
        for (LuckyNumber luckyNumber : luckyNumbers) {
            indexes.add(luckyNumber.getAllocationIndex());
        }
        if (luckyNumbers.size() != transaction.getQuantity() || indexes.size() != transaction.getQuantity()) {
            throw new IllegalStateException("Lucky-number batch does not match transaction quantity.");
        }
        for (int expectedIndex = 1; expectedIndex <= transaction.getQuantity(); expectedIndex++) {
            if (!indexes.contains(expectedIndex)) {
                throw new IllegalStateException("Lucky-number batch does not match transaction quantity.");
            }
        }
    }

    private record NumberRange(int min, int max, int width) {

        long capacity() {
            return (long) max - min + 1;
        }

        String format(int number) {
            return String.format("%0" + width + "d", number);
        }
    }

    private record AllocationProgress(int primaryBatches, int fallbackBatches, int rejectedCandidates) {}
}
