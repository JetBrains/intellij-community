/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/**
 * @author max
 */
public class PagedFileStorage {
  private ByteBufferUtil.ByteBufferHolder myHolder;
  private final File myFile;
  private long mySize;
  @NonNls private static final String RW = "rw";

  public PagedFileStorage(File file) throws IOException {
    myFile = file;
    map();
  }

  private void map() throws IOException {
    RandomAccessFile raf = new RandomAccessFile(myFile, RW);
    myHolder = new ByteBufferUtil.ByteBufferHolder(
      raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, raf.length()), myFile
    );
    raf.close();
    mySize = myFile.length();
  }


  private ByteBuffer getBuffer() {
    return myHolder.getBuffer();
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
    final ByteBuffer buffer = getBuffer();
    buffer.position(index);
    buffer.get(dst, offset, length);
  }

  public void put(int index, byte[] src, int offset, int length) {
    final ByteBuffer buffer = getBuffer();
    buffer.position(index);
    buffer.put(src, offset, length);
  }

  public void flush() {
    final ByteBuffer buffer = getBuffer();
    if (buffer instanceof MappedByteBuffer) {
      final MappedByteBuffer mappedByteBuffer = (MappedByteBuffer)buffer;
      mappedByteBuffer.force();
    }
  }

  public void close() {
    unmap();
  }

  public void resize(int size) throws IOException {
    final int current = (int)myFile.length();
    if (current == size) return;
    unmap();
    if (size > current) {
      FileOutputStream stream = new FileOutputStream(myFile, true);
      FileChannel channel = stream.getChannel();

      byte[] temp = new byte[size - current];
      Arrays.fill(temp, (byte)0);
      channel.write(ByteBuffer.wrap(temp));

      channel.force(true);
      channel.close();
    }
    else {
      RandomAccessFile raf = new RandomAccessFile(myFile, RW);
      raf.setLength(size);
      raf.close();
    }

    map();
  }

  public final long length() {
    return mySize;
  }

  private void unmap() {
    if (myHolder != null) {
      flush();
      ByteBufferUtil.unmapMappedByteBuffer(myHolder);
    }
  }
}
