package com.terradb.buffer;

public class BufferPoolMetrics {
    public long hits = 0;
    public long misses = 0;
    public long evictions = 0;
    public long diskReads = 0;
    public long diskWrites = 0;
}