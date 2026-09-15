package com.terradb.buffer;

import com.terradb.common.StorageException;

public class BufferPoolFullException extends StorageException {
    public BufferPoolFullException(String message) {
        super(message);
    }
}