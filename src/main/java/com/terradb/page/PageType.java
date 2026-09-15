package com.terradb.page;

import com.terradb.common.StorageException;

public enum PageType {

    INVALID((byte) 0),
    META((byte) 1),
    FREE_LIST((byte) 2),
    BTREE_INTERNAL((byte) 3),
    BTREE_LEAF((byte) 4);

    private final byte code;

    PageType(byte code) {
        this.code = code;
    }

    public byte code() {
        return code;
    }
    public static PageType fromCode(byte code) {
        for (PageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new StorageException("Unknown page type code: " + code);
    }
}