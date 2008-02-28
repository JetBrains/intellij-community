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

  /**
   * Reads bytes pointed by tailChunkAddress into result passed, returns new address if linked list compactification have been performed
   */
  public long readBytes(long tailChunkAddress, byte[] result) throws IOException {
    int size = result.length;
    if (size == 0) return tailChunkAddress;

    myAppender.flush();
    int bytesRead = 0;
    long chunk = tailChunkAddress;
    int chunkCount = 0;
    while (chunk != 0) {
      myReader.seek(chunk);
      final long prevChunkAddress = myReader.readLong();
      final int chunkSize = myReader.readInt();
      myReader.read(result, size - bytesRead - chunkSize, chunkSize);
      chunk = prevChunkAddress;
      bytesRead += chunkSize;
      chunkCount++;
    }
    
    assert bytesRead == size;

    if (chunkCount > 1) {
      return appendBytes(result, 0);
    }

    return tailChunkAddress;
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
