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
  private final byte[] buffer = new byte[8];

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

  private void map() {
    myHolder = new ReadWriteMappedBufferWrapper(myFile);
    myRealSize = myFile.length();
    if (LOG.isDebugEnabled()) {
      LOG.assertTrue(myPosition > 0L && myPosition < myRealSize || myPosition == 0 && myRealSize == 0, "myPosition=" + myPosition + ", myRealSize=" + myRealSize);
    }
    myHolder.buf().position((int)myPosition);
  }

  public short getShort(int index) throws IOException {
    seek(index);
    return readShort();
  }

  public short readShort() throws IOException {
    get(buffer, 0, 2);

    return Bits.getShort(buffer, 0);
  }

  public void putShort(int index, short value) throws IOException {
    seek(index);
    writeShort(value);
  }

  public void writeShort(int value) throws IOException {
    Bits.putShort(buffer, 0, (short)value);
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
      map();
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
    if (myIsDirty) {
      writeLength(mySize);
    }
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
    return Bits.getInt(buffer, 0);
  }

  public long readLong() throws IOException {
    get(buffer, 0, 8);
    return Bits.getLong(buffer, 0);
  }

  public void writeInt(int value) throws IOException {
    Bits.putInt(buffer, 0, value);
    put(buffer, 0, 4);
  }

  public void writeLong(long value) throws IOException {
    Bits.putLong(buffer, 0, value);
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
