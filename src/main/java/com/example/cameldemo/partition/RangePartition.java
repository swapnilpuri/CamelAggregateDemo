package com.example.cameldemo.partition;

/**
 * Represents a single ID-range partition of BANK_TRANSACTIONS.
 * Sent via seda:fetchPartition so each worker fetches an independent slice.
 */
public class RangePartition {

    private final int  partitionId;
    private final long startId;
    private final long endId;

    public RangePartition(int partitionId, long startId, long endId) {
        this.partitionId = partitionId;
        this.startId     = startId;
        this.endId       = endId;
    }

    public int  getPartitionId() { return partitionId; }
    public long getStartId()     { return startId; }
    public long getEndId()       { return endId; }

    /** Approximate row count for this partition (range may contain gaps). */
    public long getRowCount()    { return endId - startId + 1; }

    @Override
    public String toString() {
        return "RangePartition{id=" + partitionId +
               ", start=" + startId + ", end=" + endId + "}";
    }
}
