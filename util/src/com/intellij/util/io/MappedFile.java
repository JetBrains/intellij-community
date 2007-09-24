/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.io;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

/**
 * @author max
 */
public class MappedFile implements Forceable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.MappedFile");

  private MappedBufferWrapper myHolder;
  private final File myFile;

  private long myRealSize;
  private long mySize;
  private long myPosition;
  private boolean myIsDirty = false;

  @NonNls private static final String UTF_8_CHARSET_NAME = "UTF-8";
  @NonNls private static final String RW = "rw";
  private byte[] buffer = new byte[8];

  public MappedFile(File file, int initialSize) throws IOException {
    myFile = file;
    if (!file.exists() || file.length() == 0) {
      writeLength(0);
    }

    myPosition = 0;
    map();

    mySize = readLength();
    if (mySize == 0) {
      resize(initialSize);
    }
  }

  private long readLength() {
    File lengthFile = getLengthFile();
    DataInputStream stream = null;
    try {
      stream = new DataInputStream(new FileInputStream(lengthFile));
      return stream.readLong();
    }
    catch (IOException e) {
      writeLength(myRealSize);
      return myRealSize;
    }
    finally {
      if (stream != null) {
        try {
          stream.close();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  private File getLengthFile() {
    return new File(myFile.getPath() + ".len");
  }

  private void writeLength(final long len) {
    File lengthFile = getLengthFile();
    DataOutputStream stream = null;
    try {
      stream = new DataOutputStream(new FileOutputStream(lengthFile));
      stream.writeLong(len);
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      if (stream != null) {
        try {
          stream.close();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  private void map() throws IOException {
    myHolder = new ReadWriteMappedBufferWrapper(myFile);
    myRealSize = myFile.length();
    if (LOG.isDebugEnabled()) {
      LOG.assertTrue(myPosition >= 0L && myPosition < myRealSize, "myPosition=" + myPosition + ", myRealSize=" + myRealSize);
    }
    myHolder.buf().position((int)myPosition);
  }

  public short getShort(int index) throws IOException {
    seek(index);
    return readShort();
  }

  public short readShort() throws IOException {
    get(buffer, 0, 2);

    int ch1 = buffer[0] & 0xff;
    int ch2 = buffer[1] & 0xff;
    return (short)((ch1 << 8) + ch2);
  }

  public void putShort(int index, short value) throws IOException {
    seek(index);
    writeShort(value);
  }

  public void writeShort(int value) throws IOException {
    buffer[0] = (byte)((value >>> 8) & 0xFF);
    buffer[1] = (byte)(value & 0xFF);
    put(buffer, 0, 2);
  }

  public int getInt(int index) throws IOException {
    seek(index);
    return readInt();
  }

  public long getLong(final int index) throws IOException {
    seek(index);
    return readLong();
  }

  public void putInt(int index, int value) throws IOException {
    seek(index);
    writeInt(value);
  }

  public void putLong(final int index, final long value) throws IOException {
    seek(index);
    writeLong(value);
  }

  public byte get(int index) throws IOException {
    seek(index);
    return readByte();
  }

  public void put(int index, byte value) throws IOException {
    seek(index);
    writeByte(value);
  }

  public void get(int index, byte[] dst, int offset, int length) throws IOException {
    seek(index);
    get(dst, offset, length);
  }

  public void get(final byte[] dst, final int offset, final int length) throws IOException {
    if (myPosition + length > mySize) {
      throw new EOFException();
    }

    buf().get(dst, offset, length);
    myPosition += length;
  }

  public void put(int index, byte[] src, int offset, int length) throws IOException {
    seek(index);
    put(src, offset, length);
  }

  public void seek(long pos) throws IOException {
    ensureSize(pos);
    buf().position((int)pos);
    myPosition = pos;
    if (pos > mySize) {
      mySize = pos;
    }
  }

  private ByteBuffer buf() {
    if (!isMapped()) {
      try {
        map();
      }
      catch (IOException e) {
        LOG.error(e); // TODO: rethrow?
      }
    }

    return myHolder.buf();
  }

  private void ensureSize(final long pos) throws IOException {
    while (pos >= myRealSize) {
      expand();
    }
  }

  private void expand() throws IOException {
    resize((int)((myRealSize + 1) * 13) >> 3);
  }

  public void put(final byte[] src, final int offset, final int length) throws IOException {
    ensureSize(myPosition + length);
    myIsDirty = true;
    buf().put(src, offset, length);
    myPosition += length;
    if (myPosition > mySize) {
      mySize = myPosition;
    }
  }

  public void flush() {
    if (myIsDirty) {
      writeLength(mySize);
      final ByteBuffer buf = buf();
      if (buf instanceof MappedByteBuffer) {
        ((MappedByteBuffer)buf).force();
      }
      myIsDirty = false;
    }
  }

  public void force() {
    flush();
  }

  public boolean isDirty() {
    return myIsDirty;
  }

  public void close() {
    writeLength(mySize);
    unmap();
  }

  public void resize(int size) throws IOException {
    final int current = (int)myRealSize;
    if (current == size) return;
    unmap();
    RandomAccessFile raf = new RandomAccessFile(myFile, RW);
    try {
      raf.setLength(size);
    }
    finally {
      raf.close();
    }
    map();
  }

  public final long length() {
    return mySize;
  }

  public long getFilePointer() {
    return myPosition;
  }

  public int readInt() throws IOException {
    get(buffer, 0, 4);
    int ch1 = buffer[0] & 0xff;
    int ch2 = buffer[1] & 0xff;
    int ch3 = buffer[2] & 0xff;
    int ch4 = buffer[3] & 0xff;
    return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4);
  }

  public long readLong() throws IOException {
    get(buffer, 0, 8);

    long ch1 = buffer[0] & 0xff;
    long ch2 = buffer[1] & 0xff;
    long ch3 = buffer[2] & 0xff;
    long ch4 = buffer[3] & 0xff;
    long ch5 = buffer[4] & 0xff;
    long ch6 = buffer[5] & 0xff;
    long ch7 = buffer[6] & 0xff;
    long ch8 = buffer[7] & 0xff;
    return (ch1 << 56) + (ch2 << 48) + (ch3 << 40) + (ch4 << 32) + (ch5 << 24) + (ch6 << 16) + (ch7 << 8) + ch8;
  }

  public void writeInt(int value) throws IOException {
    buffer[0] = (byte)((value >>> 24) & 0xFF);
    buffer[1] = (byte)((value >>> 16) & 0xFF);
    buffer[2] = (byte)((value >>> 8) & 0xFF);
    buffer[3] = (byte)(value & 0xFF);
    put(buffer, 0, 4);
  }

  public void writeLong(long value) throws IOException {
    buffer[0] = (byte)((value >>> 56) & 0xFF);
    buffer[1] = (byte)((value >>> 48) & 0xFF);
    buffer[2] = (byte)((value >>> 40) & 0xFF);
    buffer[3] = (byte)((value >>> 32) & 0xFF);
    buffer[4] = (byte)((value >>> 24) & 0xFF);
    buffer[5] = (byte)((value >>> 16) & 0xFF);
    buffer[6] = (byte)((value >>> 8) & 0xFF);
    buffer[7] = (byte)(value & 0xFF);
    put(buffer, 0, 8);
  }

  public String readUTF() throws IOException {
    try {
      int len = readInt();
      byte[] bytes = new byte[ len ];
      get(bytes, 0, len);
      return new String(bytes, UTF_8_CHARSET_NAME);
    }
    catch (UnsupportedEncodingException e) {
      // Can't be
      return "";
    }
  }

  public void writeUTF(String value) throws IOException {
    try {
      final byte[] bytes = value.getBytes(UTF_8_CHARSET_NAME);
      writeInt(bytes.length);
      put(bytes, 0, bytes.length);
    }
    catch (UnsupportedEncodingException e) {
      // Can't be
    }
  }

  public int readUnsignedShort() throws IOException {
    get(buffer, 0, 2);

    int ch1 = buffer[0] & 0xff;
    int ch2 = buffer[1] & 0xff;
    return (ch1 << 8) + ch2;
  }

  public char readChar() throws IOException {
    return (char)readUnsignedShort();
  }

  public void writeChar(char value) throws IOException {
    writeShort(value);
  }

  public byte readByte() throws IOException {
    get(buffer, 0, 1);
    return buffer[0];
  }

  public void writeByte(byte value) throws IOException {
    buffer[0] = value;
    put(buffer, 0, 1);
  }

  private void unmap() {
    if (myHolder != null) {
     /* flush(); TODO: Don't commit... */
      myHolder.unmap();
    }
  }

  public boolean isMapped() {
    return myHolder.isMapped();
  }
}
