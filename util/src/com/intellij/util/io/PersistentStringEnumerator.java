/*
 * Copyright (c) 2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.io;

import com.intellij.openapi.Forceable;

import java.io.File;
import java.io.IOException;

/**
 * @author max
 */
public class PersistentStringEnumerator implements Forceable {
  private MappedFile myStorage;
  private static final int FIRST_VECTOR_OFFSET = 4;
  private static final int DIRTY_MAGIC = 0xbabe0589;
  private static final int CORRECTLY_CLOSED_MAGIC = 0xebabafac;
  private static final int STRING_HEADER_SIZE = 9;
  private static final int BITS_PER_LEVEL = 4;
  private static final int SLOTS_PER_VECTOR = 1 << BITS_PER_LEVEL;
  private static final int LEVEL_MASK = SLOTS_PER_VECTOR - 1;
  private static final byte[] EMPTY_VECTOR = new byte[SLOTS_PER_VECTOR * 4];

  private byte[] buffer = new byte[255 + STRING_HEADER_SIZE];
  private boolean myClosed = false;
  private boolean myDirty = false;

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
      markDirty(true);
      allocVector();
    }
    else {
      int sign;
      try {
        sign = myStorage.getInt(0);
      }
      catch(Exception e) {
        sign = DIRTY_MAGIC;
      }
      if (sign != CORRECTLY_CLOSED_MAGIC) {
        myStorage.close();
        throw new CorruptedException(file);
      }
    }
  }

  public synchronized int enumerate(String value) throws IOException {
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
      final int newId = writeNewString(value, valueHC);
      myStorage.putInt(pos, -newId);
      return newId;
    }
    else {
      int collision = -vector;
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

      final int newId = writeNewString(value, valueHC);
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
    myStorage.seek(pos);
    myStorage.put(EMPTY_VECTOR, 0, EMPTY_VECTOR.length);
    return pos;
  }

  private int nextCanditate(final int idx) throws IOException {
    return -myStorage.getInt(idx);
  }

  private static boolean isAscii(final String str) {
    for (int i = 0; i != str.length(); ++ i) {
      final char c = str.charAt(i);
      if (c < 0 || c >= 128) {
        return false;
      }
    }
    return true;
  }

  private int writeNewString(final String value, int hashCode) {
    try {
      markDirty(true);

      final MappedFile storage = myStorage;
      final int pos = (int)storage.length();
      storage.seek(pos);

      int len = value.length();
      if (len < 255 && isAscii(value)) {
        final byte[] buf = buffer;
        buf[0] = buf[1] = buf[2] = buf[3] = 0;
        buf[7] = (byte)(hashCode & 0xFF);
        hashCode >>>= 8;
        buf[6] = (byte)(hashCode & 0xFF);
        hashCode >>>= 8;
        buf[5] = (byte)(hashCode & 0xFF);
        hashCode >>>= 8;
        buf[4] = (byte)(hashCode & 0xFF);
        buf[8] = (byte)len;
        for (int i = 0; i < len; i++) {
          buf[i + STRING_HEADER_SIZE] = (byte)value.charAt(i);
        }
        storage.put(buf, 0, len + STRING_HEADER_SIZE);
      }
      else {
        storage.writeInt(0);
        storage.writeInt(hashCode);
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

  public synchronized String valueOf(int idx) throws IOException {
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
      try {
        flush();
      }
      finally {
        myStorage.close();
      }
    }
  }

  public boolean isClosed() {
    return myClosed;
  }

  public boolean isDirty() {
    return myDirty;
  }

  public void flush() throws IOException {
    if (myStorage.isMapped() || isDirty()) {
      markDirty(false);
      myStorage.flush();
    }
  }

  public void force() {
    try {
      flush();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void markDirty(boolean dirty) throws IOException {
    if (myDirty) {
      if (!dirty) {
        myStorage.putInt(0, CORRECTLY_CLOSED_MAGIC);
        myDirty = false;
      }
    }
    else {
      if (dirty) {
        myStorage.putInt(0, DIRTY_MAGIC);
        myDirty = true;
      }
    }
  }
}
