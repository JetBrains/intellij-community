/*
 * Copyright (c) 2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.io;

import java.io.File;
import java.io.IOException;

/**
 * @author max
 */
public class PersistentStringEnumerator {
  private MappedFile myStorage;
  private static final int FIRST_VECTOR_OFFSET = 4;
  private static final int DIRTY_MAGIC = 0xbabe0589;
  private static final int CORRECTLY_CLOSED_MAGIC = 0xebabafac;

  private byte[] buffer = new byte[256];

  private static final int BITS_PER_LEVEL = 4;
  private static final int SLOTS_PER_VECTOR = 1 << BITS_PER_LEVEL;
  private static final int LEVEL_MASK = SLOTS_PER_VECTOR - 1;
  private static final byte[] EMPTY_VECTOR = new byte[SLOTS_PER_VECTOR * 4];
  private boolean myClosed = false;

  public static class CorruptedException extends IOException {
    @SuppressWarnings({"HardCodedStringLiteral"})
    public CorruptedException(File file) {
      super("PersistentStringEnumerator storage corrupted " + file.getPath());
    }
  }

  public PersistentStringEnumerator(File file) throws IOException {
    this(file, 1024 * 4);
  }

  public PersistentStringEnumerator(File file, int initialSize) throws IOException {
    myStorage = new MappedFile(file, initialSize);
    if (myStorage.length() == 0) {
      myStorage.writeInt(DIRTY_MAGIC);
      allocVector();
    }
    else if (myStorage.getInt(0) != CORRECTLY_CLOSED_MAGIC) {
      myStorage.close();
      throw new CorruptedException(file);
    }
    else {
      myStorage.putInt(0, DIRTY_MAGIC);
    }
  }

  public int enumerate(String value) throws IOException {
    myStorage.putInt(0, DIRTY_MAGIC);

    int depth = 0;
    final int valueHC = value.hashCode();
    int hc = valueHC;
    int vector = FIRST_VECTOR_OFFSET;
    int pos;
    int lastVector;

    do {
      lastVector = vector;
      pos = vector + (hc & LEVEL_MASK) * 4;
      hc >>>= BITS_PER_LEVEL;
      vector = myStorage.getInt(pos);
      depth++;
    }
    while (vector > 0);

    if (vector == 0) {
      // Empty slot
      final int newId = writeNewString(value);
      myStorage.putInt(pos, -newId);
      return newId;
    }
    else {
      int collision = Math.abs(vector);
      boolean splitVector = false;
      int candidateHC;
      do {
        candidateHC = hashCodeOf(collision);
        if (candidateHC != valueHC) {
          splitVector = true;
          break;
        }

        String candidate = valueOf(collision);
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
          final int valueHCByte = hcByte(valueHC, depth);
          final int oldHCByte = hcByte(candidateHC, depth);
          if (valueHCByte == oldHCByte) {
            int newVector = allocVector();
            myStorage.putInt(lastVector + oldHCByte * 4, newVector);
            lastVector = newVector;
          }
          else {
            myStorage.putInt(lastVector + valueHCByte * 4, -newId);
            myStorage.putInt(lastVector + oldHCByte * 4, vector);
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

  private static int hcByte(int hashcode, int byteN) {
    return (hashcode >>> (byteN * BITS_PER_LEVEL)) & LEVEL_MASK;
  }

  private int allocVector() throws IOException {
    final int pos = (int)myStorage.length();
    myStorage.put(pos, EMPTY_VECTOR, 0, EMPTY_VECTOR.length);
    return pos;
  }

  private int nextCanditate(final int idx) throws IOException {
    return -myStorage.getInt(idx);
  }

  public static boolean isAscii(final String str) {
    for (int i = 0; i != str.length(); ++ i) {
      final char c = str.charAt(i);
      if (c < 0 || c >= 128) {
        return false;
      }
    }
    return true;
  }

  private int writeNewString(final String value) {
    try {
      final MappedFile storage = myStorage;
      final int pos = (int)storage.length();
      storage.seek(pos);
      storage.writeInt(0);
      storage.writeInt(value.hashCode());

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

  public int hashCodeOf(int idx) throws IOException {
    return myStorage.getInt(idx + 4);
  }

  public String valueOf(int idx) throws IOException {
    final String result;
    final MappedFile storage = myStorage;
    storage.seek(idx + 8);
    int len = 0xFF & (int)storage.readByte();
    if (len == 0xFF) {
      result = storage.readUTF();
    }
    else {
      final byte[] buf = buffer;
      storage.get(buf, 0, len);
      char[] chars = new char[len];
      for (int i = 0; i < len; i++) {
        chars[i] = (char)buf[i];
      }
      result = new String(chars);
    }

    return result;
  }

  public void close() throws IOException {
    if (!myClosed) {
      myClosed = true;
      flush();
      myStorage.close();
    }
  }

  public boolean isClosed() {
    return myClosed;
  }

  public void flush() throws IOException {
    myStorage.putInt(0, CORRECTLY_CLOSED_MAGIC);
    myStorage.flush();
  }
}
