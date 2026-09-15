package com.terradb.btree;

import com.terradb.common.TerraDBConstants;
import com.terradb.page.Page;
import com.terradb.page.PageType;

public class LeafNode {
    public static final int OFF_PARENT = 0;
    public static final int OFF_NEXT = 4;
    public static final int OFF_PREV = 8;
    public static final int OFF_COUNT = 12;
    public static final int OFF_ENTRIES = 16;
    public static final int ENTRY_SIZE = 16;
    public static final int MAX_ENTRIES = 100;
    public static final int MIN_ENTRIES = MAX_ENTRIES / 2;

    public final Page page;

    public LeafNode(Page page) {
        this.page = page;
        page.setPageType(PageType.BTREE_LEAF);
    }

    public void initNew(int parentId) {
        setParent(parentId);
        setNext(TerraDBConstants.INVALID_PAGE_ID);
        setPrev(TerraDBConstants.INVALID_PAGE_ID);
        setCount(0);
    }

    public int getPageId() { return page.getPageId(); }
    public int getParent() { return page.getPayloadInt(OFF_PARENT); }
    public void setParent(int p) { page.putPayloadInt(OFF_PARENT, p); }
    public int getNext() { return page.getPayloadInt(OFF_NEXT); }
    public void setNext(int n) { page.putPayloadInt(OFF_NEXT, n); }
    public int getPrev() { return page.getPayloadInt(OFF_PREV); }
    public void setPrev(int p) { page.putPayloadInt(OFF_PREV, p); }
    public int getCount() { return page.getPayloadInt(OFF_COUNT); }
    public void setCount(int c) { page.putPayloadInt(OFF_COUNT, c); page.setItemCount(c); }

    public long getKey(int i) { return page.getPayloadLong(OFF_ENTRIES + i * ENTRY_SIZE); }
    public long getVal(int i) { return page.getPayloadLong(OFF_ENTRIES + i * ENTRY_SIZE + 8); }
    public void setKey(int i, long k) { page.putPayloadLong(OFF_ENTRIES + i * ENTRY_SIZE, k); }
    public void setVal(int i, long v) { page.putPayloadLong(OFF_ENTRIES + i * ENTRY_SIZE + 8, v); }

    public int findKeyIndex(long key) {
        int count = getCount();
        for (int i = 0; i < count; i++) {
            if (getKey(i) == key) return i;
            if (getKey(i) > key) return -1;
        }
        return -1;
    }

    public void insertSorted(long key, long value) {
        int count = getCount();
        int pos = count;
        for (int i = 0; i < count; i++) {
            if (getKey(i) > key) { pos = i; break; }
            else if (getKey(i) == key) { setVal(i, value); return; }
        }
        for (int i = count; i > pos; i--) { setKey(i, getKey(i - 1)); setVal(i, getVal(i - 1)); }
        setKey(pos, key);
        setVal(pos, value);
        setCount(count + 1);
    }

    public void append(long key, long value) {
        int count = getCount();
        setKey(count, key);
        setVal(count, value);
        setCount(count + 1);
    }

    public void prepend(long key, long value) {
        int count = getCount();
        for (int i = count; i > 0; i--) { setKey(i, getKey(i - 1)); setVal(i, getVal(i - 1)); }
        setKey(0, key);
        setVal(0, value);
        setCount(count + 1);
    }

    public void removeAt(int idx) {
        int count = getCount();
        for (int i = idx; i < count - 1; i++) { setKey(i, getKey(i + 1)); setVal(i, getVal(i + 1)); }
        setCount(count - 1);
    }

    public long split(LeafNode newLeaf) {
        int count = getCount();
        int mid = count / 2;
        for (int i = mid; i < count; i++) {
            newLeaf.setKey(i - mid, getKey(i));
            newLeaf.setVal(i - mid, getVal(i));
        }
        newLeaf.setCount(count - mid);
        this.setCount(mid);
        return newLeaf.getKey(0);
    }
}