package com.weddingraffle.rifa.service;

public interface CapacityReservationService {

    void reserve(String externalReference, int quantity);

    CapacityAllocationResult allocate(String externalReference, int quantity);

    void releaseAllocation(String externalReference);

    int expireActiveReservations();
}
