package com.terradb.wal;

import com.terradb.common.StorageException;

public enum WalRecordType {
    OPERATION_COMMIT(1),
    CHECKPOINT(2);

    private final int code;

    WalRecordType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static WalRecordType fromCode(int code) {
        for (WalRecordType t : values()) {
            if (t.code == code) return t;
        }
        throw new StorageException("Unknown WAL record type: " + code);
    }
}