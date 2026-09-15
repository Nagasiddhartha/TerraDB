package com.terradb.core;

public enum CrashPoint {
    NONE,
    BEFORE_WAL,
    AFTER_WAL_APPEND,
    AFTER_WAL_FSYNC,
    AFTER_DATA_MOD,
    AFTER_DATA_FLUSH,
    BEFORE_CHECKPOINT_COMPLETE
}