/*
 * @author max
 */
package com.intellij.util.io;

import java.io.*;

public class PersistentHashMapValueStorage {
  private final DataOutputStream myAppender;
  private final RandomAccessFile myReader;
  private long mySize;

  public PersistentHashMapValueStorage(String path) throws IOException {
    myAppender = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path, true)));
    myReader = new RandomAccessFile(path, "r");
    mySize = myReader.length();

    if (mySize == 0) {
      appendBytes("Header Record For PersistentHashMapValuStorage".getBytes(), 0);
    }
  }

  public long appendBytes(byte[] data, long prevChunkAddress) throws IOException {
    long result = mySize;
    myAppender.writeLong(prevChunkAddress);
    myAppender.writeInt(data.length);
    myAppender.write(data);
    mySize += data.length + 8 + 4;

    return result;
  }

  public byte[] readBytes(long tailChunkAddress, int size) throws IOException {
    myAppender.flush();
    if (size == 0) return new byte[0];

    byte[] result = new byte[size];
    int bytesRead = 0;
    long chunk = tailChunkAddress;
    while (chunk != 0) {
      myReader.seek(chunk);
      final long prevChunkAddress = myReader.readLong();
      final int chunkSize = myReader.readInt();
      myReader.read(result, size - bytesRead - chunkSize, chunkSize);
      chunk = prevChunkAddress;
      bytesRead += chunkSize;
    }
    assert bytesRead == size;

    return result;
  }

  public void force() {
    try {
      myAppender.flush();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void dispose() {
    try {
      myAppender.close();
      myReader.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static PersistentHashMapValueStorage create(final String path) throws IOException {
    return new PersistentHashMapValueStorage(path);
  }
}
