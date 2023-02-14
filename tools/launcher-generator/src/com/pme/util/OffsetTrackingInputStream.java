// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.util;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;


public class OffsetTrackingInputStream implements DataInput {
  private final DataInputStream myBaseStream;
  private long myOffset = 0;

  public OffsetTrackingInputStream(DataInputStream baseStream) {
    myBaseStream = baseStream;
  }

  public long getOffset() {
    return myOffset;
  }

  @Override
  public void readFully(byte[] b) throws IOException {
    myBaseStream.readFully(b);
    myOffset += b.length;
  }

  @Override
  public void readFully(byte[] b, int off, int len) throws IOException {
    myBaseStream.readFully(b, off, len);
    myOffset += len;
  }

  @Override
  public int skipBytes(int n) throws IOException {
    int skipped = myBaseStream.skipBytes(n);
    myOffset += skipped;
    return skipped;
  }

  @Override
  public boolean readBoolean() throws IOException {
    myOffset++;
    return myBaseStream.readBoolean();
  }

  @Override
  public byte readByte() throws IOException {
    myOffset++;
    return myBaseStream.readByte();
  }

  @Override
  public int readUnsignedByte() throws IOException {
    myOffset++;
    return myBaseStream.readUnsignedByte();
  }

  @Override
  public short readShort() throws IOException {
    myOffset += 2;
    return myBaseStream.readShort();
  }

  @Override
  public int readUnsignedShort() throws IOException {
    myOffset += 2;
    return myBaseStream.readUnsignedShort();
  }

  @Override
  public char readChar() throws IOException {
    myOffset += 2;
    return myBaseStream.readChar();
  }

  @Override
  public int readInt() throws IOException {
    myOffset += 4;
    return myBaseStream.readInt();
  }

  @Override
  public long readLong() throws IOException {
    myOffset += 8;
    return myBaseStream.readLong();
  }

  @Override
  public float readFloat() throws IOException {
    myOffset += 4;
    return myBaseStream.readFloat();
  }

  @Override
  public double readDouble() throws IOException {
    myOffset += 8;
    return myBaseStream.readDouble();
  }

  @Override
  public String readLine() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String readUTF() {
    throw new UnsupportedOperationException();
  }
}
