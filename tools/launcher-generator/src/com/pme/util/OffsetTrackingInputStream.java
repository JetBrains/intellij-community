/*
 * Copyright 2006 ProductiveMe Inc.
 * Copyright 2013 JetBrains s.r.o.
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

package com.pme.util;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * @author yole
 */
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
  public String readLine() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String readUTF() throws IOException {
    throw new UnsupportedOperationException();
  }
}
