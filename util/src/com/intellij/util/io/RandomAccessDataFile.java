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

import com.intellij.openapi.Forceable;

import java.io.*;
import java.nio.ByteBuffer;

public class RandomAccessDataFile implements Forceable {
  private final static OpenChannelsCache ourCache = new OpenChannelsCache(150, "rw");
  private static int ourFilesCount = 0;

  private final int myCount = ourFilesCount++;
  private final File myFile;
  private final PagePool myPool;

  private final byte[] myTypedIOBuffer = new byte[8];

  private volatile long mySize;
  private volatile boolean myIsDirty = false;

  public RandomAccessDataFile(final File file) throws IOException {
    this(file, PagePool.SHARED);
  }

  public RandomAccessDataFile(final File file, final PagePool pool) throws IOException {
    myPool = pool;
    myFile = file;
    if (!file.exists()) {
      throw new FileNotFoundException(file.getPath() + " does not exist");
    }

    mySize = file.length();
  }

  public void put(long addr, byte[] bytes, int off, int len) {
    myIsDirty = true;
    mySize = Math.max(mySize, addr + len);

    while (len > 0) {
      final Page page = myPool.alloc(this, addr);
      int written = page.put(addr, bytes, off, len);
      len -= written;
      addr += written;
      off += written;
    }
  }

  public void get(long addr, byte[] bytes, int off, int len) {
    while (len > 0) {
      final Page page = myPool.alloc(this, addr);
      int read = page.get(addr, bytes, off, len);
      len -= read;
      addr += read;
      off += read;
    }
  }

  private void releaseFile() {
    ourCache.releaseChannel(myFile);
  }

  private RandomAccessFile getFile() throws FileNotFoundException {
    return ourCache.getChannel(myFile);
  }

  public void putInt(long addr, int value) {
    byte[] buffer = myTypedIOBuffer;
    buffer[0] = (byte)((value >>> 24) & 0xFF);
    buffer[1] = (byte)((value >>> 16) & 0xFF);
    buffer[2] = (byte)((value >>> 8) & 0xFF);
    buffer[3] = (byte)(value & 0xFF);

    put(addr, buffer, 0, 4);
  }

  public int getInt(long addr) {
    byte[] buffer = myTypedIOBuffer;
    get(addr, buffer, 0, 4);

    int ch1 = buffer[0] & 0xff;
    int ch2 = buffer[1] & 0xff;
    int ch3 = buffer[2] & 0xff;
    int ch4 = buffer[3] & 0xff;
    return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4);
  }

  public void putLong(long addr, long value) {
    byte[] buffer = myTypedIOBuffer;
    buffer[0] = (byte)((value >>> 56) & 0xFF);
    buffer[1] = (byte)((value >>> 48) & 0xFF);
    buffer[2] = (byte)((value >>> 40) & 0xFF);
    buffer[3] = (byte)((value >>> 32) & 0xFF);
    buffer[4] = (byte)((value >>> 24) & 0xFF);
    buffer[5] = (byte)((value >>> 16) & 0xFF);
    buffer[6] = (byte)((value >>> 8) & 0xFF);
    buffer[7] = (byte)(value & 0xFF);

    put(addr, buffer, 0, 8);
  }

  public void putByte(final long addr, final byte b) {
    myTypedIOBuffer[0] = b;
    put(addr, myTypedIOBuffer, 0, 1);
  }

  public byte getByte(long addr) {
    get(addr, myTypedIOBuffer, 0, 1);
    return myTypedIOBuffer[0];
  }

  public String getUTF(long addr) {
    try {
      int len = getInt(addr);
      byte[] bytes = new byte[ len ];
      get(addr + 4, bytes, 0, len);
      return new String(bytes, "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      // Can't be
      return "";
    }
  }

  public void putUTF(long addr, String value) {
    try {
      final byte[] bytes = value.getBytes("UTF-8");
      putInt(addr, bytes.length);
      put(addr + 4, bytes, 0, bytes.length);
    }
    catch (UnsupportedEncodingException e) {
      // Can't be
    }
  }

  public long getLong(long addr) {
    byte[] buffer = myTypedIOBuffer;
    get(addr, buffer, 0, 8);

    long ch1 = buffer[0] & 0xff;
    long ch2 = buffer[1] & 0xff;
    long ch3 = buffer[2] & 0xff;
    long ch4 = buffer[3] & 0xff;
    long ch5 = buffer[4] & 0xff;
    long ch6 = buffer[5] & 0xff;
    long ch7 = buffer[6] & 0xff;
    long ch8 = buffer[7] & 0xff;

    return ((ch1 << 56) + (ch2 << 48) + (ch3 << 40) + (ch4 << 32) + (ch5 << 24) + (ch6 << 16) + (ch7 << 8) + ch8);
  }

  public long length() {
    return mySize;
  }

  public void dispose() {
    myPool.flushPages(this);
    ourCache.closeChannel(myFile);
  }

  public void force() {
    if (isDirty()) {
      myPool.flushPages(this);
      myIsDirty = false;
    }
  }

  public boolean isDirty() {
    return myIsDirty;
  }

  public static int totalReads = 0;
  public static long totalReadBytes = 0;

  public static int totalWrites = 0;
  public static long totalWriteBytes = 0;

  public void loadPage(final Page page) {
    try {
      final RandomAccessFile file = getFile();
      try {
        synchronized (file) {
          file.seek(page.getOffset());
          final ByteBuffer buf = page.getBuf();

          totalReads++;
          totalReadBytes += Page.PAGE_SIZE;

//          System.out.println("Read at: \t" + page.getOffset() + "\t len: " + Page.PAGE_SIZE);
          file.read(buf.array(), 0, Page.PAGE_SIZE);
        }
      }
      finally {
        releaseFile();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void flushPage(final Page page, int start, int end) {
    try {
      flush(page.getBuf(), page.getOffset() + start, start, end - start);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void flush(final ByteBuffer buf, final long fileOffset, final int bufOffset, int length) throws IOException {
    if (fileOffset + length > mySize) {
      length = (int)(mySize - fileOffset);
    }

    final RandomAccessFile file = getFile();
    try {
      synchronized (file) {
        file.seek(fileOffset);
        totalWrites++;
        totalWriteBytes += length;

//        System.out.println("Write at: \t" + fileOffset + "\t len: " + length);
        file.write(buf.array(), bufOffset, length);
      }
    }
    finally {
      releaseFile();
    }
  }

  public int hashCode() {
    return myCount;
  }

  @Override
  public synchronized String toString() {
    return "RandomAccessFile[" + myFile + ", dirty=" + myIsDirty + "]";
  }
}
