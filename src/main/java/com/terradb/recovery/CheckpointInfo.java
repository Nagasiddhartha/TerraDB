package com.terradb.recovery;

public record CheckpointInfo(long lsn, long offset) {
}