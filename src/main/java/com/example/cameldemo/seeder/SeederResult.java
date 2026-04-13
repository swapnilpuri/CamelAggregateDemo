package com.example.cameldemo.seeder;

/**
 * Returned by DataSeederService and serialised as JSON from SeederController.
 */
public record SeederResult(
        int    requestedCount,
        int    insertedCount,
        long   durationMs,
        double recordsPerSecond,
        long   oracleTotalRows    // total rows in BANK_TRANSACTIONS after the insert
) {}
