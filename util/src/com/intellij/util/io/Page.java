/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.util.io;

import com.intellij.util.containers.LimitedPool;

import java.nio.ByteBuffer;
import java.util.BitSet;

public class Page {
  public static final int PAGE_SIZE = 8 * 1024;

  private static final LimitedPool<ByteBuffer> ourBufferPool = new LimitedPool<ByteBuffer>(10, new LimitedPool.ObjectFactory<ByteBuffer>() {
    public ByteBuffer create() {
      return ByteBuffer.allocate(PAGE_SIZE);
    }

    public void cleanup(final ByteBuffer byteBuffer) {
    }
  });

  private final long offset;
  private final RandomAccessDataFile owner;
  private final PoolPageKey myKey;

  private ByteBuffer buf;
  private boolean read = false;
  private boolean dirty = false;
  private long myFinalizationId;
  private BitSet myWriteMask;

  public Page(RandomAccessDataFile owner, long offset) {
    this.owner = owner;
    this.offset = offset;

    myKey = new PoolPageKey(owner, offset);
    read = false;
    dirty = false;
    myWriteMask = null;

    assert offset >= 0;
  }

  private void ensureRead() {
    if (!read) {
      if (myWriteMask != null) {
        byte[] content = new byte[PAGE_SIZE];
        final ByteBuffer b = getBuf();
        b.position(0);
        b.get(content, 0, PAGE_SIZE);

        owner.loadPage(this);
        for(int i=myWriteMask.nextSetBit(0); i>=0; i=myWriteMask.nextSetBit(i+1)) {
          b.put(i, content[i]);
        }
        myWriteMask = null;
      }
      else {
        owner.loadPage(this);
      }

      read = true;
    }
  }

  private void ensureReadOrWriteMaskExists() {
    dirty = true;
    if (read || myWriteMask != null) return;
    myWriteMask = new BitSet(PAGE_SIZE);
  }

  public synchronized void flush() {
    if (dirty) {
      if (myWriteMask != null) {
        if (myWriteMask.cardinality() < PAGE_SIZE) {
          ensureRead();
        }
        myWriteMask = null;
      }
      owner.flushPage(this);
      dirty = false;
    }
  }

  public synchronized ByteBuffer getBuf() {
    if (buf == null) {
      synchronized (ourBufferPool) {
        buf = ourBufferPool.alloc();
      }
    }
    return buf;
  }

  public synchronized void recycle() {
    if (buf != null) {
      synchronized (ourBufferPool) {
        ourBufferPool.recycle(buf);
      }
    }

    buf = null;
    read = false;
    dirty = false;
    myWriteMask = null;
  }

  public long getOffset() {
    return offset;
  }

  public synchronized int put(long index, byte[] bytes, int off, int length) {
    myFinalizationId = 0;
    ensureReadOrWriteMaskExists();

    final int start = (int)(index - offset);
    final ByteBuffer b = getBuf();
    b.position(start);

    int count = Math.min(length, PAGE_SIZE - start);
    b.put(bytes, off, count);

    if (myWriteMask != null) {
      myWriteMask.set(start, start + count);
    }
    return count;
  }

  public synchronized int get(long index, byte[] bytes, int off, int length) {
    myFinalizationId = 0;
    ensureRead();

    final int start = (int)(index - offset);
    final ByteBuffer b = getBuf();
    b.position(start);

    int count = Math.min(length, PAGE_SIZE - start);
    b.get(bytes, off, count);

    return count;
  }

  public synchronized boolean isDirty() {
    return dirty;
  }

  public RandomAccessDataFile getOwner() {
    return owner;
  }

  public synchronized void setFinalizationId(final long curFinalizationId) {
    myFinalizationId = curFinalizationId;
  }

  public PoolPageKey getKey() {
    return myKey;
  }

  public synchronized boolean flushIfFinalizationIdIsEqualTo(final long finalizationId) {
    if (myFinalizationId == finalizationId) {
      flush();
      return true;
    }

    return false;
  }

  public synchronized boolean recycleIfFinalizationIdIsEqualTo(final long finalizationId) {
    if (myFinalizationId == finalizationId) {
      recycle();
      return true;
    }
    return false;
  }
}