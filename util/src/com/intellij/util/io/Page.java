/*
 * @author max
 */
package com.intellij.util.io;

import java.nio.ByteBuffer;

public class Page {
  public static final int PAGE_SIZE = 8 * 1024;

  private final ByteBuffer buf;
  private final long offset;
  private final RandomAccessDataFile owner;
  private boolean dirty = false;
  private static long totalAccessCount = 0;
  private long pageAccessCount = 0;
  private boolean myIsLocked = false;

  public Page(final RandomAccessDataFile owner, final long offset) {
    buf = ByteBuffer.allocate(PAGE_SIZE);
    this.owner = owner;
    this.offset = offset;
    touch(false);
    owner.loadPage(this);
  }

  public boolean intersects(RandomAccessDataFile anotherOwner, long anotherOffset, int anotherLen) {
    if (anotherOwner != owner) return false;
    return Math.max(offset, anotherOffset) <= Math.min(offset + buf.limit(), anotherOffset + anotherLen);
  }

  public void flush() {
    if (dirty) {
      owner.flushPage(this);
    }
  }

  public long getPageAccessCount() {
    return pageAccessCount;
  }

  public ByteBuffer getBuf() {
    return buf;
  }

  public long getOffset() {
    return offset;
  }

  @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
  private void touch(final boolean write) {
    pageAccessCount = ++totalAccessCount;
    dirty |= write;
  }

  public int put(long index, byte[] bytes, int off, int length) {
    touch(true);

    final int start = (int)(index - offset);
    buf.position(start);

    int count = Math.min(length, PAGE_SIZE - start);
    buf.put(bytes, off, count);

    return count;
  }

  public int get(long index, byte[] bytes, int off, int length) {
    touch(false);

    final int start = (int)(index - offset);
    buf.position(start);

    int count = Math.min(length, PAGE_SIZE - start);
    buf.get(bytes, off, count);

    return count;
  }

  public boolean isLocked() {
    return myIsLocked;
  }

  public void lock() {
    myIsLocked = true;
  }

  public void unlock() {
    myIsLocked = false;
  }

  public RandomAccessDataFile getOwner() {
    return owner;
  }
}