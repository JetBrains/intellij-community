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
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.SLRUMap;
import com.intellij.util.containers.ShareableKey;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 * @author jeka
 */
public class PersistentEnumerator<Data> implements Forceable {
  protected static final int NULL_ID = 0;
  protected static final int DATA_OFFSET = 8;
  private static final int META_DATA_OFFSET = 4;
  private static final int FIRST_VECTOR_OFFSET = 8;
  private static final int DIRTY_MAGIC = 0xbabe0589;
  private static final int VERSION = 3;
  private static final int CORRECTLY_CLOSED_MAGIC = 0xebabafac + VERSION;

  private static final int BITS_PER_LEVEL = 5;
  private static final int SLOTS_PER_VECTOR = 1 << BITS_PER_LEVEL;
  private static final int LEVEL_MASK = SLOTS_PER_VECTOR - 1;
  private static final byte[] EMPTY_VECTOR = new byte[SLOTS_PER_VECTOR * 4];

  private static final int BITS_PER_FIRST_LEVEL = 12;
  private static final int SLOTS_PER_FIRST_VECTOR = 1 << BITS_PER_FIRST_LEVEL;
  private static final int FIRST_LEVEL_MASK = SLOTS_PER_FIRST_VECTOR - 1;
  private static final byte[] FIRST_VECTOR = new byte[SLOTS_PER_FIRST_VECTOR * 4];

  protected final MappedFile myStorage;
  private final MappedFileDataOutput myOut;
  private final MappedFileDataInput myIn;

  private boolean myClosed = false;
  private boolean myDirty = false;
  private final DataDescriptor<Data> myDataDescriptor;
  private final byte[] myBuffer = new byte[8];

  private static final CacheKey ourFlyweight = new CacheKey(null, null);

  private static class CacheKey implements ShareableKey {
    public PersistentEnumerator owner;
    public Object key;

    private CacheKey(final Object key, final PersistentEnumerator owner) {
      this.key = key;
      this.owner = owner;
    }

    public ShareableKey getStableCopy() {
      return new CacheKey(key, owner);
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof CacheKey)) return false;

      final CacheKey cacheKey = (CacheKey)o;

      if (!key.equals(cacheKey.key)) return false;
      if (!owner.equals(cacheKey.owner)) return false;

      return true;
    }

    public int hashCode() {
      return key.hashCode();
    }
  }

  private static CacheKey sharedKey(Object key, PersistentEnumerator owner) {
    ourFlyweight.key = key;
    ourFlyweight.owner = owner;
    return ourFlyweight;
  }

  private static final SLRUMap<Object, Integer> ourEnumerationCache = new SLRUMap<Object, Integer>(8192, 8192);

  public static class CorruptedException extends IOException {
    @SuppressWarnings({"HardCodedStringLiteral"})
    public CorruptedException(File file) {
      super("PersistentStringEnumerator storage corrupted " + file.getPath());
    }
  }

  public static interface DataDescriptor<T> extends EqualityPolicy<T>, DataExternalizer<T> {
  }
  
  public PersistentEnumerator(File file, DataDescriptor<Data> dataDescriptor, int initialSize) throws IOException {
    myDataDescriptor = dataDescriptor;
    myStorage = new MappedFile(file, initialSize);
    myOut = new MappedFileDataOutput(myStorage);
    myIn = new MappedFileDataInput(myStorage);
    
    if (myStorage.length() == 0) {
      markDirty(true);
      putMetaData(0);
      allocVector(FIRST_VECTOR);
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
  
  protected synchronized int tryEnumerate(Data value) throws IOException {
    synchronized (ourEnumerationCache) {
      final Integer cachedId = ourEnumerationCache.get(sharedKey(value, this));
      if (cachedId != null) return cachedId.intValue();
    }
    final int id = enumerateImpl(value, false);
    if (id != NULL_ID) {
      synchronized (ourEnumerationCache) {
        ourEnumerationCache.put(new CacheKey(value, this), id);
      }
    }
    return id;
  }
  
  public synchronized int enumerate(Data value) throws IOException {
    synchronized (ourEnumerationCache) {
      final Integer cachedId = ourEnumerationCache.get(sharedKey(value, this));
      if (cachedId != null) return cachedId.intValue();
    }

    final int id = enumerateImpl(value, true);
    synchronized (ourEnumerationCache) {
      ourEnumerationCache.put(new CacheKey(value, this), id);
    }

    return id;
  }

  public interface DataFilter {
    boolean accept(int id);
  }

  public synchronized void putMetaData(int data) throws IOException {
    myStorage.putInt(META_DATA_OFFSET, data);
  }

  public synchronized int getMetaData() throws IOException {
    return myStorage.getInt(META_DATA_OFFSET);
  }

  public boolean processAllDataObject(final Processor<Data> processor, @Nullable final DataFilter filter) throws IOException {
    return traverseAllRecords(new RecordsProcessor() {
      public boolean process(final int record) throws IOException {
        if (filter == null || filter.accept(record)) {
          return processor.process(valueOf(record));
        }
        return true;
      }
    });

  }

  public Collection<Data> getAllDataObjects(@Nullable final DataFilter filter) throws IOException {
    final List<Data> values = new ArrayList<Data>();
    processAllDataObject(new CommonProcessors.CollectProcessor<Data>(values), filter);
    return values;
  }

  public interface RecordsProcessor {
    boolean process(int record) throws IOException;
  }

  public synchronized boolean traverseAllRecords(RecordsProcessor p) throws IOException {
    return traverseRecords(FIRST_VECTOR_OFFSET, SLOTS_PER_FIRST_VECTOR, p);
  }

  private boolean traverseRecords(int vectorStart, int slotsCount, RecordsProcessor p) throws IOException {
    for (int slotIdx = 0; slotIdx < slotsCount; slotIdx++) {
      final int vector = myStorage.getInt(vectorStart + slotIdx * 4);
      if (vector < 0) {
        for (int record = -vector; record != 0; record = nextCanditate(record)) {
          if (!p.process(record)) return false;
        }
      }
      else if (vector > 0) {
        if (!traverseRecords(vector, SLOTS_PER_VECTOR, p)) return false;
      }
    }
    return true;
  }

  private int enumerateImpl(final Data value, final boolean saveNewValue) throws IOException {
    int depth = 0;
    final int valueHC = myDataDescriptor.getHashCode(value);
    int hc = valueHC;
    int vector = FIRST_VECTOR_OFFSET;
    int pos;
    int lastVector;

    int levelMask = FIRST_LEVEL_MASK;
    int bitsPerLevel = BITS_PER_FIRST_LEVEL;
    do {
      lastVector = vector;
      pos = vector + (hc & levelMask) * 4;
      hc >>>= bitsPerLevel;
      vector = myStorage.getInt(pos);
      depth++;

      levelMask = LEVEL_MASK;
      bitsPerLevel = BITS_PER_LEVEL;
    }
    while (vector > 0);

    if (vector == 0) {
      // Empty slot
      if (!saveNewValue) {
        return NULL_ID;
      }
      final int newId = writeData(value, valueHC);
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

        Data candidate = valueOf(collision);
        if (myDataDescriptor.isEqual(value, candidate)) {
          return collision;
        }

        collision = nextCanditate(collision);
      }
      while (collision != 0);
      
      if (!saveNewValue) {
        return NULL_ID;
      }
      
      final int newId = writeData(value, valueHC);
      if (splitVector) {
        depth--;
        do {
          final int valueHCByte = hcByte(valueHC, depth);
          final int oldHCByte = hcByte(candidateHC, depth);
          if (valueHCByte == oldHCByte) {
            int newVector = allocVector(EMPTY_VECTOR);
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
    if (byteN == 0) {
      return hashcode & FIRST_LEVEL_MASK;
    }

    hashcode >>>= BITS_PER_FIRST_LEVEL;
    byteN--;
    
    return (hashcode >>> (byteN * BITS_PER_LEVEL)) & LEVEL_MASK;
  }

  private int allocVector(final byte[] empty) throws IOException {
    final int pos = (int)myStorage.length();
    myStorage.seek(pos);
    myStorage.put(empty, 0, empty.length);
    return pos;
  }

  private int nextCanditate(final int idx) throws IOException {
    return -myStorage.getInt(idx);
  }

  private int writeData(final Data value, int hashCode) {
    try {
      markDirty(true);

      final MappedFile storage = myStorage;
      final int pos = (int)storage.length();
      storage.seek(pos);

      //storage.writeInt(0);
      //storage.writeInt(hashCode);
      final byte[] buf = myBuffer;
      buf[0] = buf[1] = buf[2] = buf[3] = 0;
      buf[7] = (byte)(hashCode & 0xFF);
      hashCode >>>= 8;
      buf[6] = (byte)(hashCode & 0xFF);
      hashCode >>>= 8;
      buf[5] = (byte)(hashCode & 0xFF);
      hashCode >>>= 8;
      buf[4] = (byte)(hashCode & 0xFF);
      myStorage.put(buf, 0, buf.length);

      myDataDescriptor.save(myOut, value);
      onNewValueAdded(value);

      return pos;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void onNewValueAdded(final Data data) {
  }

  private int hashCodeOf(int idx) throws IOException {
    return myStorage.getInt(idx + 4);
  }

  public synchronized Data valueOf(int idx) throws IOException {
    final MappedFile storage = myStorage;
    storage.seek(idx + DATA_OFFSET);
    return myDataDescriptor.read(myIn);
  }

  public synchronized void close() throws IOException {
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

  public synchronized boolean isClosed() {
    return myClosed;
  }

  public synchronized boolean isDirty() {
    return myDirty;
  }

  public synchronized void flush() throws IOException {
    if (myStorage.isMapped() || isDirty()) {
      markDirty(false);
      myStorage.flush();
    }
  }

  public synchronized void force() {
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
        markClean();
      }
    }
    else {
      if (dirty) {
        myStorage.putInt(0, DIRTY_MAGIC);
        myDirty = true;
      }
    }
  }

  protected void markClean() throws IOException {
    myStorage.putInt(0, CORRECTLY_CLOSED_MAGIC);
    myDirty = false;
  }

  private static final class MappedFileDataInput implements DataInput {
    private final MappedFile myFile;

    private MappedFileDataInput(MappedFile file) {
      myFile = file;
    }

    public void readFully(final byte[] b) throws IOException {
      myFile.get(b, 0, b.length);
    }

    public void readFully(final byte[] b, final int off, final int len) throws IOException {
      myFile.get(b, off, len);
    }

    public int skipBytes(final int n) throws IOException {
      myFile.seek(myFile.getFilePointer() + n);
      return n;
    }

    public boolean readBoolean() throws IOException {
      final byte b = myFile.readByte();
      return b != 0;
    }

    public int readUnsignedByte() throws IOException {
      return ((int)myFile.readByte()) & 0xFF;
    }

    public short readShort() throws IOException {
      return myFile.readShort();
    }

    public float readFloat() throws IOException {
      return Float.intBitsToFloat(myFile.readInt());
    }

    public double readDouble() throws IOException {
      return Double.longBitsToDouble(myFile.readLong());
    }

    public String readLine() throws IOException {
      throw new UnsupportedOperationException();
    }

    public int readInt() throws IOException {
      return myFile.readInt();
    }

    public long readLong() throws IOException {
      return myFile.readLong();
    }

    public String readUTF() throws IOException {
      return myFile.readUTF();
    }

    public int readUnsignedShort() throws IOException {
      return myFile.readUnsignedShort();
    }

    public char readChar() throws IOException {
      return myFile.readChar();
    }

    public byte readByte() throws IOException {
      return myFile.readByte();
    }
  }
  
  private static final class MappedFileDataOutput implements DataOutput {
    private final MappedFile myFile;

    public MappedFileDataOutput(MappedFile file) {
      myFile = file;
    }

    public void writeShort(final int value) throws IOException {
      myFile.writeShort(value);
    }

    public void write(final int b) throws IOException {
      myFile.writeByte((byte)(b & 0xFF));
    }

    public void write(final byte[] b) throws IOException {
      myFile.put(b, 0, b.length);
    }

    public void write(final byte[] b, final int off, final int len) throws IOException {
      myFile.put(b, off, len);
    }

    public void writeBoolean(final boolean v) throws IOException {
      myFile.writeByte(v? (byte)1 : 0);
    }

    public void writeByte(final int v) throws IOException {
      write(v);
    }

    public void writeChar(final int v) throws IOException {
      myFile.writeShort((short)(v & 0xFFFF));
    }

    public void writeFloat(final float v) throws IOException {
      myFile.writeInt(Float.floatToIntBits(v));
    }

    public void writeDouble(final double v) throws IOException {
      myFile.writeLong(Double.doubleToLongBits(v));
    }

    public void writeBytes(final String s) throws IOException {
      for (int idx = 0; idx < s.length(); idx++) {
        final char ch = s.charAt(idx);
        myFile.writeByte((byte)(ch & 0xFF));
      }
    }

    public void writeChars(final String s) throws IOException {
      for (int idx = 0; idx < s.length(); idx++) {
        final char ch = s.charAt(idx);
        myFile.writeChar(ch);
      }
    }

    public void writeInt(final int value) throws IOException {
      myFile.writeInt(value);
    }

    public void writeLong(final long value) throws IOException {
      myFile.writeLong(value);
    }

    public void writeUTF(final String value) throws IOException {
      myFile.writeUTF(value);
    }
  }
}