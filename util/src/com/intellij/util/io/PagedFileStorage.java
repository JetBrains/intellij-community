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
  private MappedBufferWrapper myBuffer;
  private final File myFile;
  private long mySize = -1;
  @NonNls private static final String RW = "rw";

  public PagedFileStorage(File file) throws IOException {
    myFile = file;
  }

  private void map() throws IOException {
    myBuffer = new ReadWriteMappedBufferWrapper(myFile);
    mySize = myFile.length();
  }

  public short getShort(int index) {
    return getBuffer().getShort(index);
  }

  public void putShort(int index, short value) {
    getBuffer().putShort(index, value);
  }

  public int getInt(int index) {
    return getBuffer().getInt(index);
  }

  public void putInt(int index, int value) {
    getBuffer().putInt(index, value);
  }

  public byte get(int index) {
    return getBuffer().get(index);
  }

  public void put(int index, byte value) {
    getBuffer().put(index, value);
  }

  public void get(int index, byte[] dst, int offset, int length) {
    final ByteBuffer buffer = getBuffer().duplicate();
    buffer.position(index);
    buffer.get(dst, offset, length);
  }

  public void put(int index, byte[] src, int offset, int length) {
    final ByteBuffer buffer = getBuffer().duplicate();
    buffer.position(index);
    buffer.put(src, offset, length);
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


  public final long length() {
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

  private void unmap() {
    if (myBuffer != null) {
      myBuffer.unmap();
      myBuffer = null;
    }
  }

  private ByteBuffer getBuffer() {
    return myBuffer.buf();
  }

  public void flush() {
    myBuffer.flush();
  }
}
