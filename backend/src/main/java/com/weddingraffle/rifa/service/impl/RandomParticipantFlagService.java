package com.weddingraffle.rifa.service.impl;

import com.weddingraffle.rifa.entity.ParticipantFlag;
import com.weddingraffle.rifa.repository.TransactionRepository;
import com.weddingraffle.rifa.service.ParticipantFlagService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class RandomParticipantFlagService implements ParticipantFlagService {

    private static final String FLAGS_RESOURCE = "participant-flags.csv";
    private static final List<ParticipantFlag> FLAGS = loadFlags();

    private final TransactionRepository transactionRepository;

    public RandomParticipantFlagService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public ParticipantFlag resolveForPhone(String phone) {
        return transactionRepository
                .findFirstByPhoneAndParticipantFlagCodeIsNotNullOrderByCreatedAtAsc(phone)
                .map(transaction -> new ParticipantFlag(
                        transaction.getParticipantFlagCode(),
                        transaction.getParticipantFlagName(),
                        transaction.getParticipantFlagEmoji()))
                .orElseGet(this::randomUnusedFlag);
    }

    private ParticipantFlag randomUnusedFlag() {
        Set<String> usedFlagCodes = Set.copyOf(transactionRepository.findDistinctParticipantFlagCodes());
        List<ParticipantFlag> availableFlags = FLAGS.stream()
                .filter(flag -> !usedFlagCodes.contains(flag.code()))
                .toList();

        if (availableFlags.isEmpty()) {
            throw new IllegalStateException("No participant flags available for a new phone.");
        }

        return availableFlags.get(ThreadLocalRandom.current().nextInt(availableFlags.size()));
    }

    private static List<ParticipantFlag> loadFlags() {
        try (var input = RandomParticipantFlagService.class.getClassLoader().getResourceAsStream(FLAGS_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Participant flags resource not found.");
            }

            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                List<ParticipantFlag> flags = reader.lines()
                        .filter(line -> !line.isBlank() && !line.startsWith("#"))
                        .map(RandomParticipantFlagService::toParticipantFlag)
                        .toList();

                if (flags.isEmpty()) {
                    throw new IllegalStateException("Participant flags resource is empty.");
                }

                validateFlags(flags);
                return flags;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load participant flags resource.", exception);
        }
    }

    private static ParticipantFlag toParticipantFlag(String line) {
        String[] parts = line.split(";", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new IllegalStateException("Invalid participant flag line: " + line);
        }
        String code = parts[0].trim();
        if (!code.matches("[A-Z0-9_]+")) {
            throw new IllegalStateException("Invalid participant flag code: " + code);
        }
        return new ParticipantFlag(code, parts[1].trim(), parts[2].trim());
    }

    private static void validateFlags(List<ParticipantFlag> flags) {
        Set<String> uniqueCodes = new HashSet<>();
        for (ParticipantFlag flag : flags) {
            if (!uniqueCodes.add(flag.code())) {
                throw new IllegalStateException("Duplicate participant flag code: " + flag.code());
            }
        }
    }
}
