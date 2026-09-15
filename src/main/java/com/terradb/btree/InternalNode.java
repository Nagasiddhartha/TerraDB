package com.terradb.btree;

import com.terradb.common.TerraDBConstants;
import com.terradb.page.Page;
import com.terradb.page.PageType;

public class InternalNode {
    public static final int OFF_PARENT = 0;
    public static final int OFF_COUNT = 4;
    public static final int OFF_CHILDREN = 8;
    public static final int MAX_KEYS = 100;
    public static final int MIN_KEYS = MAX_KEYS / 2;

    public final Page page;

    public InternalNode(Page page) {
        this.page = page;
        page.setPageType(PageType.BTREE_INTERNAL);
    }

    public void initNew(int parentId) {
        setParent(parentId);
        setCount(0);
    }

    public int getPageId() { return page.getPageId(); }
    public int getParent() { return page.getPayloadInt(OFF_PARENT); }
    public void setParent(int p) { page.putPayloadInt(OFF_PARENT, p); }
    public int getCount() { return page.getPayloadInt(OFF_COUNT); }
    public void setCount(int c) { page.putPayloadInt(OFF_COUNT, c); page.setItemCount(c); }

    public int getChild(int i) { return page.getPayloadInt(OFF_CHILDREN + i * 4); }
    public void setChild(int i, int c) { page.putPayloadInt(OFF_CHILDREN + i * 4, c); }

    private int keysStart() { return OFF_CHILDREN + (MAX_KEYS + 1) * 4; }
    public long getKey(int i) { return page.getPayloadLong(keysStart() + i * 8); }
    public void setKey(int i, long k) { page.putPayloadLong(keysStart() + i * 8, k); }

    public int findChildIndex(long key) {
        int count = getCount();
        for (int i = 0; i < count; i++) {
            if (key < getKey(i)) return i;
        }
        return count;
    }

    public int indexOfChild(int childId) {
        int count = getCount();
        for (int i = 0; i <= count; i++) {
            if (getChild(i) == childId) return i;
        }
        return -1;
    }

    public void insertSorted(long key, int rightChildId) {
        int count = getCount();
        int pos = count;
        for (int i = 0; i < count; i++) {
            if (getKey(i) > key) { pos = i; break; }
        }
        for (int i = count; i > pos; i--) { setKey(i, getKey(i - 1)); setChild(i + 1, getChild(i)); }
        setKey(pos, key);
        setChild(pos + 1, rightChildId);
        setCount(count + 1);
    }

    public void append(long key, int rightChild) {
        int count = getCount();
        setKey(count, key);
        setChild(count + 1, rightChild);
        setCount(count + 1);
    }

    public void prepend(long key, int leftChild) {
        int count = getCount();
        for (int i = count; i >= 0; i--) setChild(i + 1, getChild(i));
        setChild(0, leftChild);
        for (int i = count - 1; i >= 0; i--) setKey(i + 1, getKey(i));
        setKey(0, key);
        setCount(count + 1);
    }

    public void removeAt(int keyIdx) {
        int count = getCount();
        for (int i = keyIdx; i < count - 1; i++) setKey(i, getKey(i + 1));
        for (int i = keyIdx + 1; i < count; i++) setChild(i, getChild(i + 1));
        setCount(count - 1);
    }

    public void removeFirst() {
        int count = getCount();
        for (int i = 0; i < count - 1; i++) setKey(i, getKey(i + 1));
        for (int i = 0; i < count; i++) setChild(i, getChild(i + 1));
        setCount(count - 1);
    }

    public void removeLast() {
        setCount(getCount() - 1);
    }

    public long split(InternalNode newNode) {
        int count = getCount();
        int mid = count / 2;
        long promoteKey = getKey(mid);
        int newCount = 0;
        for (int i = mid + 1; i < count; i++) {
            newNode.setKey(newCount, getKey(i));
            newNode.setChild(newCount, getChild(i));
            newCount++;
        }
        newNode.setChild(newCount, getChild(count));
        newNode.setCount(newCount);
        this.setCount(mid);
        return promoteKey;
    }
}