package com.weddingraffle.rifa.repository;

import java.util.List;

public interface LuckyNumberAllocationRepository {

    List<String> insertCandidates(long transactionId, String email, List<LuckyNumberAllocationCandidate> candidates);

    List<String> insertRandomAvailable(
            long transactionId,
            String email,
            int numberMin,
            int numberMax,
            int numberWidth,
            int batchSize,
            int firstTemporaryIndex,
            int randomSeed);

    long countAvailable(int numberMin, int numberMax, int numberWidth);

    int compactAllocationIndexes(long transactionId);
}
