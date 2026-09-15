package com.terradb.btree;

import com.terradb.common.StorageException;

public class BTreeException extends StorageException {
    public BTreeException(String message) { super(message); }
}