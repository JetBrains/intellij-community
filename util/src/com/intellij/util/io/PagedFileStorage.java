/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author max
 */
public final class PagedFileStorage {
  private final static int BUFFER_SIZE = 512 * 1024; // half a meg
  private MappedBufferWrapper[] myBuffers = null;

  private final File myFile;
  private long mySize = -1;
  @NonNls private static final String RW = "rw";

  public PagedFileStorage(File file) throws IOException {
    myFile = file;
  }

  private synchronized void map() throws IOException {
    mySize = myFile.length();
    int intSize = (int)mySize;
    myBuffers = new MappedBufferWrapper[intSize / BUFFER_SIZE + 1];
    for (int i = 0; i < myBuffers.length; i++) {
      final int offset = i * BUFFER_SIZE;
      if (offset < intSize) {
        myBuffers[i] = new ReadWriteMappedBufferWrapper(myFile, offset, Math.min(intSize - offset, BUFFER_SIZE));
      }
    }
  }

  public short getShort(int index) {
    int page = index / BUFFER_SIZE;
    int offset = index % BUFFER_SIZE;

    return getBuffer(page).getShort(offset);
  }

  public void putShort(int index, short value) {
    int page = index / BUFFER_SIZE;
    int offset = index % BUFFER_SIZE;

    getBuffer(page).putShort(offset, value);
  }

  public int getInt(int index) {
    int page = index / BUFFER_SIZE;
    int offset = index % BUFFER_SIZE;

    return getBuffer(page).getInt(offset);
  }

  public void putInt(int index, int value) {
    int page = index / BUFFER_SIZE;
    int offset = index % BUFFER_SIZE;

    getBuffer(page).putInt(offset, value);
  }

  public byte get(int index) {
    int page = index / BUFFER_SIZE;
    int offset = index % BUFFER_SIZE;

    return getBuffer(page).get(offset);
  }

  public void put(int index, byte value) {
    int page = index / BUFFER_SIZE;
    int offset = index % BUFFER_SIZE;

    getBuffer(page).put(offset, value);
  }

  public void get(int index, byte[] dst, int offset, int length) {
    int i = index;
    int o = offset;
    int l = length;

    while (l > 0) {
      int page = i / BUFFER_SIZE;
      int page_offset = i % BUFFER_SIZE;

      int page_len = Math.min(l, BUFFER_SIZE - page_offset);
      final ByteBuffer buffer = getBuffer(page);
      buffer.position(page_offset);
      buffer.get(dst, o, page_len);

      l -= page_len;
      o += page_len;
      i += page_len;
    }
  }

  public void put(int index, byte[] src, int offset, int length) {
    int i = index;
    int o = offset;
    int l = length;

    while (l > 0) {
      int page = i / BUFFER_SIZE;
      int page_offset = i % BUFFER_SIZE;

      int page_len = Math.min(l, BUFFER_SIZE - page_offset);
      final ByteBuffer buffer = getBuffer(page);
      buffer.position(page_offset);
      buffer.put(src, o, page_len);

      l -= page_len;
      o += page_len;
      i += page_len;
    }
  }

  public void close() {
    unmap();
  }

  public void resize(int newSize) throws IOException {
    int oldSize = (int)myFile.length();
    if (oldSize == newSize) return;

    unmap();
    resizeFile(newSize);
    map();

    // it is not guaranteed that new portition will consist of null
    // after resize, so we should fill it manually
    int delta = newSize - oldSize;
    if (delta > 0) fillWithZeros(oldSize, delta);
  }

  private void resizeFile(int newSzie) throws IOException {
    RandomAccessFile raf = new RandomAccessFile(myFile, RW);
    try {
      raf.setLength(newSzie);
    }
    finally {
      raf.close();
    }
  }

  private void fillWithZeros(int from, int length) {
    byte[] buff = new byte[length];
    Arrays.fill(buff, (byte)0);
    put(from, buff, 0, buff.length);
  }


  public synchronized final long length() {
    if (mySize == -1) {
      try {
        map();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return mySize;
  }

  private synchronized void unmap() {
    if (myBuffers != null) {
      for (int i = 0; i < myBuffers.length; i++) {
        MappedBufferWrapper buffer = myBuffers[i];
        if (buffer != null) {
          buffer.dispose();
          myBuffers[i] = null;
        }
      }
    }
  }

  private ByteBuffer getBuffer(int page) {
    return myBuffers[page].buf();
  }

  public void flush() {
    for (MappedBufferWrapper wrapper : myBuffers) {
      wrapper.flush();
    }
  }
}
