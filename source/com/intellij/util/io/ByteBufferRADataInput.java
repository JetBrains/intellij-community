package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author max
 */
public class ByteBufferRADataInput implements RandomAccessDataInput {
  private static final Logger LOG = Logger.getInstance("com.intellij.util.io.ByteBufferRADataInput");

  private ByteBuffer myBuffer;

  public ByteBufferRADataInput(ByteBuffer buffer) {
    myBuffer = buffer;
  }

  public void setPosition(int pos) {
    myBuffer.position(pos);
  }

  public int getPosition() {
    return myBuffer.position();
  }

  public void readFully(byte b[]) throws IOException {
    myBuffer.get(b);
  }

  public void readFully(byte b[], int off, int len) throws IOException {
    myBuffer.get(b, off, len);
  }

  public int skipBytes(int n) throws IOException {
    int newPos = getPosition() + n;
    setPosition(newPos);
    return newPos;
  }

  public boolean readBoolean() throws IOException {
    return myBuffer.get() == 1;
  }

  public byte readByte() throws IOException {
    return myBuffer.get();
  }

  public int readUnsignedByte() throws IOException {
    return 0xFF & ((int)myBuffer.get());
  }

  public short readShort() throws IOException {
    return myBuffer.getShort();
  }

  public int readUnsignedShort() throws IOException {
    return 0xFFFF & ((int)myBuffer.getShort());
  }

  public char readChar() throws IOException {
    return myBuffer.getChar();
  }

  public int readInt() throws IOException {
    return myBuffer.getInt();
  }

  public long readLong() throws IOException {
    return myBuffer.getLong();
  }

  public float readFloat() throws IOException {
    return myBuffer.getFloat();
  }

  public double readDouble() throws IOException {
    return myBuffer.getDouble();
  }

  public String readLine() throws IOException {
    LOG.error("Not implemented");
    return null;
  }

  public String readUTF() throws IOException {
    return DataInputStream.readUTF(this);
  }
}
