package com.terradb.buffer;

import com.terradb.page.Page;

public class BufferFrame {
    int pageId;
    Page page;
    int pinCount;
    boolean dirty;
    boolean committed;
    long lastAccess;
}