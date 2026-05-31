package com.example.reservation.domain.model;

public enum ReservationStatus {

    RESERVING,

    PENDING,

    CONFIRMING,

    CONFIRMED,

    CANCELLING,

    CANCELLED,

    REJECTED,

    FAILED_RETRYABLE
}
