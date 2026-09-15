package com.terradb.page;

import com.terradb.common.StorageException;

public class CorruptedPageException extends StorageException {

    public CorruptedPageException(String message) {
        super(message);
    }
}