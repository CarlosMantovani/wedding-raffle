package com.weddingraffle.rifa.service.impl;

import com.weddingraffle.rifa.dto.FlagRankingResponse;
import com.weddingraffle.rifa.dto.HomeSummaryResponse;
import com.weddingraffle.rifa.dto.RaffleDrawResponse;
import com.weddingraffle.rifa.entity.RaffleConfig;
import com.weddingraffle.rifa.entity.RaffleDraw;
import com.weddingraffle.rifa.entity.Transaction;
import com.weddingraffle.rifa.exception.ResourceNotFoundException;
import com.weddingraffle.rifa.repository.LuckyNumberRepository;
import com.weddingraffle.rifa.repository.RaffleConfigRepository;
import com.weddingraffle.rifa.repository.RaffleDrawRepository;
import com.weddingraffle.rifa.repository.TransactionRepository;
import com.weddingraffle.rifa.service.PublicHomeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicHomeServiceImpl implements PublicHomeService {

    private static final int SUMMARY_FLAG_RANKING_SIZE = 5;
    private static final int FULL_FLAG_RANKING_SIZE = 15;

    private final RaffleConfigRepository raffleConfigRepository;
    private final RaffleDrawRepository raffleDrawRepository;
    private final LuckyNumberRepository luckyNumberRepository;
    private final TransactionRepository transactionRepository;

    public PublicHomeServiceImpl(
            RaffleConfigRepository raffleConfigRepository,
            RaffleDrawRepository raffleDrawRepository,
            LuckyNumberRepository luckyNumberRepository,
            TransactionRepository transactionRepository) {
        this.raffleConfigRepository = raffleConfigRepository;
        this.raffleDrawRepository = raffleDrawRepository;
        this.luckyNumberRepository = luckyNumberRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public HomeSummaryResponse getSummary() {
        var flagRanking = findFlagRanking(SUMMARY_FLAG_RANKING_SIZE);
        var config = raffleConfigRepository
                .findById(RaffleConfig.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Raffle config not found."));
        var raffleResult = raffleDrawRepository
                .findFirstByOrderByIdDesc()
                .map(this::toRaffleResult)
                .orElse(null);
        return new HomeSummaryResponse(config.getScheduledDrawAt(), flagRanking, raffleResult);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlagRankingResponse> getFlagRanking() {
        return findFlagRanking(FULL_FLAG_RANKING_SIZE);
    }

    private List<FlagRankingResponse> findFlagRanking(int size) {
        var flagRanking = transactionRepository.findApprovedFlagRanking(PageRequest.of(0, size));
        if (flagRanking.isEmpty()) {
            return List.of();
        }

        var approvedTotalNumbers = transactionRepository.sumApprovedQuantity();
        return IntStream.range(0, flagRanking.size())
                .mapToObj(index -> {
                    var flag = flagRanking.get(index);
                    return new FlagRankingResponse(
                            flag.getCode(),
                            flag.getName(),
                            flag.getEmoji(),
                            index + 1,
                            calculateProgressPercent(flag.getTotalNumbers(), approvedTotalNumbers));
                })
                .toList();
    }

    private static BigDecimal calculateProgressPercent(long totalNumbers, long approvedTotalNumbers) {
        if (approvedTotalNumbers <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.valueOf(totalNumbers)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(approvedTotalNumbers), 2, RoundingMode.HALF_UP);
    }

    private RaffleDrawResponse toRaffleResult(RaffleDraw raffleDraw) {
        return luckyNumberRepository
                .findByNumber(raffleDraw.getWinningNumber())
                .map(luckyNumber -> toRaffleResult(raffleDraw, luckyNumber.getTransaction()))
                .orElseGet(() -> new RaffleDrawResponse(
                        raffleDraw.getWinningNumber(), raffleDraw.getWinnerName(), raffleDraw.getDrawnAt()));
    }

    private static RaffleDrawResponse toRaffleResult(RaffleDraw raffleDraw, Transaction transaction) {
        return new RaffleDrawResponse(
                raffleDraw.getWinningNumber(),
                raffleDraw.getWinnerName(),
                raffleDraw.getDrawnAt(),
                transaction.getParticipantFlagName(),
                transaction.getParticipantFlagEmoji());
    }
}
