package com.terradb.buffer;

import com.terradb.disk.DiskManager;
import com.terradb.page.Page;

import java.util.ArrayList;
import java.util.List;

public class BufferPool {
    private final DiskManager disk;
    private final int capacity;
    private final BufferFrame[] frames;
    private final boolean walEnabled;
    public final BufferPoolMetrics metrics = new BufferPoolMetrics();

    public BufferPool(DiskManager disk, int capacity) {
        this(disk, capacity, false);
    }

    public BufferPool(DiskManager disk, int capacity, boolean walEnabled) {
        this.disk = disk;
        this.capacity = capacity;
        this.walEnabled = walEnabled;
        this.frames = new BufferFrame[capacity];
    }

    public Page pin(int pageId) {
        for (BufferFrame f : frames) {
            if (f != null && f.pageId == pageId) {
                f.pinCount++;
                f.lastAccess = System.nanoTime();
                metrics.hits++;
                return f.page;
            }
        }

        metrics.misses++;
        int idx = findEmptyOrVictim();
        if (idx == -1) throw new BufferPoolFullException("All frames pinned or uncommitted!");

        BufferFrame victim = frames[idx];
        if (victim != null && victim.page != null) {
            if (victim.dirty) {
                disk.writePage(victim.page);
                metrics.diskWrites++;
            }
            metrics.evictions++;
        } else {
            victim = new BufferFrame();
            frames[idx] = victim;
        }

        Page p = disk.readPage(pageId);
        metrics.diskReads++;
        victim.pageId = pageId;
        victim.page = p;
        victim.pinCount = 1;
        victim.dirty = false;
        victim.committed = true;
        victim.lastAccess = System.nanoTime();
        return p;
    }

    public void pinNew(Page page) {
        int idx = findEmptyOrVictim();
        if (idx == -1) throw new BufferPoolFullException("All frames pinned or uncommitted!");

        BufferFrame f = frames[idx];
        if (f == null) { f = new BufferFrame(); frames[idx] = f; }
        else if (f.dirty) { disk.writePage(f.page); metrics.diskWrites++; }

        f.pageId = page.getPageId();
        f.page = page;
        f.pinCount = 1;
        f.dirty = true;
        f.committed = !walEnabled;   // with WAL: new page is UNCOMMITTED until diary says so
        f.lastAccess = System.nanoTime();
    }

    public void unpin(Page page, boolean dirty) {
        for (BufferFrame f : frames) {
            if (f != null && f.pageId == page.getPageId()) {
                f.pinCount--;
                if (dirty) {
                    f.dirty = true;
                    if (walEnabled) f.committed = false;
                }
                return;
            }
        }
    }

    public List<Page> collectUncommitted() {
        List<Page> out = new ArrayList<>();
        for (BufferFrame f : frames) {
            if (f != null && f.page != null && f.dirty && !f.committed) out.add(f.page);
        }
        return out;
    }

    public boolean hasUncommitted() {
        for (BufferFrame f : frames) {
            if (f != null && f.page != null && f.dirty && !f.committed) return true;
        }
        return false;
    }

    public void markCommitted(long lsn) {
        for (BufferFrame f : frames) {
            if (f != null && f.page != null && f.dirty && !f.committed) {
                if (lsn >= 0) f.page.setPageLsn(lsn);
                f.committed = true;
            }
        }
    }

    public void flushAll() {
        for (BufferFrame f : frames) {
            if (f != null && f.page != null && f.dirty && f.committed) {
                disk.writePage(f.page);
                f.dirty = false;
                metrics.diskWrites++;
            }
        }
    }

    public void invalidate(int pageId) {
        for (int i = 0; i < frames.length; i++) {
            if (frames[i] != null && frames[i].pageId == pageId) {
                frames[i] = null;
            }
        }
    }

    private int findEmptyOrVictim() {
        int victimIdx = -1;
        long oldest = Long.MAX_VALUE;
        for (int i = 0; i < capacity; i++) {
            if (frames[i] == null || frames[i].page == null) return i;
            boolean evictable = frames[i].pinCount == 0
                    && !(frames[i].dirty && !frames[i].committed); // NEVER evict uncommitted
            if (evictable && frames[i].lastAccess < oldest) {
                oldest = frames[i].lastAccess;
                victimIdx = i;
            }
        }
        return victimIdx;
    }
}