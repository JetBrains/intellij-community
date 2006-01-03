/*
 * Copyright (c) 2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.io;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author max
 */
public class PersistentStringEnumerator {
  private File myFile;
  private MappedFile myStorage;
  private static final int FIRST_VECTOR_OFFSET = 0;
  private int maxDepth = 0;
  private int allocatedPages = 0;
  private byte[] buffer = new byte[256];

  private static final int BITS_PER_LEVEL = 4;
  private static final int SLOTS_PER_VECTOR = 1 << BITS_PER_LEVEL;
  private static final int LEVEL_MASK = SLOTS_PER_VECTOR - 1;

  public PersistentStringEnumerator(File file) throws IOException {
    myFile = file;
    myStorage = new MappedFile(file, 1024 * 4);
    if (myStorage.length() == 0) {
      allocVector();
    }
  }

  int enumerate(String value) {
    int depth = 0;
    try {
      int hc = value.hashCode();
      int vector = FIRST_VECTOR_OFFSET;
      int pos;
      int lastVector;

      do {
        lastVector = vector;
        pos = vector + (hc & LEVEL_MASK) * 4;
        hc >>>= BITS_PER_LEVEL;
        vector = myStorage.getInt(pos);
        depth++;
      } while (vector > 0);

      if (vector == 0) {
        // Empty slot
        final int newId = writeNewString(value);
        myStorage.putInt(pos, -newId);
        return newId;
      }
      else {
        int collision = Math.abs(vector);
        boolean splitVector = false;
        String candidate;
        do {
          candidate = valueOf(collision);
          if (candidate.hashCode() != value.hashCode()) {
            splitVector = true;
            break;
          }

          if (value.equals(candidate)) {
            return collision;
          }

          collision = nextCanditate(collision);
        }
        while (collision != 0);

        final int newId = writeNewString(value);
        if (splitVector) {
          depth--;
          do {
            final int valueHC = hcByte(value, depth);
            final int oldHC = hcByte(candidate, depth);
            if (valueHC == oldHC) {
              int newVector = allocVector();
              myStorage.putInt(lastVector + oldHC * 4, newVector);
              lastVector = newVector;
            }
            else {
              myStorage.putInt(lastVector + valueHC * 4, -newId);
              myStorage.putInt(lastVector + oldHC * 4, vector);
              break;
            }
            depth++;
          }
          while (true);
        }
        else {
          // Hashcode collision detected. Insert new string into the list of colliding.
          myStorage.putInt(newId, vector);
          myStorage.putInt(pos, -newId);
        }
        return newId;
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      if (depth > maxDepth) maxDepth = depth;
    }
  }

  private static int hcByte(String s, int byteN) {
    return (s.hashCode() >>> (byteN * BITS_PER_LEVEL)) & LEVEL_MASK;
  }

  private int allocVector() {
    try {
      final int pos = (int)myStorage.length();
      byte[] fill = new byte[SLOTS_PER_VECTOR * 4];
      Arrays.fill(fill, (byte)0);
      myStorage.put(pos, fill, 0, fill.length);
      allocatedPages++;
      return pos;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private int nextCanditate(final int idx) throws IOException {
    return -myStorage.getInt(idx);
  }

  public static boolean isAscii (final String str) {
    for (int i = 0; i != str.length(); ++ i)
    {
      final char c = str.charAt(i);
      if (c < 0 || c >= 128)
        return false;
    }
    return true;
  }

  private int writeNewString(final String value) {
    try {
      final MappedFile storage = myStorage;
      final int pos = (int)storage.length();
      storage.seek(pos);
      storage.writeInt(0);

      int len = value.length();
      if (len < 255 && isAscii(value)) {
        storage.writeByte((byte)len);
        final byte[] buf = buffer;
        for (int i = 0; i < len; i++) {
          buf[i] = (byte)value.charAt(i);
        }
        storage.put(buf, 0, len);
      }
      else {
        storage.writeByte((byte)0xFF);
        storage.writeUTF(value);
      }

      return pos;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String valueOf(int idx) {
    try {
      final MappedFile storage = myStorage;
      storage.seek(idx + 4);
      int len = 0xFF & (int) storage.readByte();
      if (len == 0xFF) {
        return storage.readUTF();
      }
      else {
        final byte[] buf = buffer;
        storage.get(buf, 0, len);
        char[] chars = new char[len];
        for (int i = 0; i < len; i++) {
          chars[i] = (char)buf[i];
        }
        return new String(chars);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void close() {
    myStorage.close();
    System.out.println("maxDepth = " + maxDepth + ", pages = " + allocatedPages);
  }
}
