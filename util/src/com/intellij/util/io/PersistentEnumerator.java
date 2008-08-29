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

import java.io.*;
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
  private static final int VERSION = 4;
  private static final int CORRECTLY_CLOSED_MAGIC = 0xebabafac + VERSION;

  private static final int BITS_PER_LEVEL = 4;
  private static final int SLOTS_PER_VECTOR = 1 << BITS_PER_LEVEL;
  private static final int LEVEL_MASK = SLOTS_PER_VECTOR - 1;
  private static final byte[] EMPTY_VECTOR = new byte[SLOTS_PER_VECTOR * 4];

  private static final int BITS_PER_FIRST_LEVEL = 12;
  private static final int SLOTS_PER_FIRST_VECTOR = 1 << BITS_PER_FIRST_LEVEL;
  private static final int FIRST_LEVEL_MASK = SLOTS_PER_FIRST_VECTOR - 1;
  private static final byte[] FIRST_VECTOR = new byte[SLOTS_PER_FIRST_VECTOR * 4];


  protected final MappedFile myStorage;

  private boolean myClosed = false;
  private boolean myDirty = false;
  private final DataDescriptor<Data> myDataDescriptor;
  private final byte[] myBuffer = new byte[RECORD_SIZE];

  private static final CacheKey ourFlyweight = new CacheKey(null, null);
  private final File myFile;
  private static final int COLLISION_OFFSET = 0;
  private static final int KEY_HASHCODE_OFFSET = COLLISION_OFFSET + 4;
  private static final int KEY_REF_OFFSET = KEY_HASHCODE_OFFSET + 4;
  protected static final int RECORD_SIZE = KEY_REF_OFFSET + 4;

  private final MyAppenderStream myKeyStream;
  private final MyDataIS myKeyReadStream;
  private final RandomAccessFile myRaf;

  private static class MyAppenderStream extends DataOutputStream {
    public MyAppenderStream(final File out) throws FileNotFoundException {
      super(new BufferedOutputStream(new FileOutputStream(out, true)));
      written = (int) out.length();
    }
  }

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
    myFile = file;
    if (!file.exists()) {
      if (!file.createNewFile()) {
        throw new IOException("Cannot create empty file: " + file);
      }
    }

    myStorage = new MappedFile(myFile, initialSize);
    myKeyStream = new MyAppenderStream(keystreamFile());

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

    myRaf = new RandomAccessFile(keystreamFile(), "r");
    myKeyReadStream = new MyDataIS(myRaf);
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
    myStorage.put(pos, empty, 0, empty.length);
    return pos;
  }

  private int nextCanditate(final int idx) throws IOException {
    return -myStorage.getInt(idx);
  }

  private int writeData(final Data value, int hashCode) {
    try {
      markDirty(true);

      byte[] buf = prepareEntryRecordBuf(hashCode, myKeyStream.size());
      myDataDescriptor.save(myKeyStream, value);

      final MappedFile storage = myStorage;
      final int pos = (int)storage.length();
      storage.put(pos, buf, 0, buf.length);

      return pos;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private byte[] prepareEntryRecordBuf(int hashCode, int dataOffset) {
    final byte[] buf = getRecordBuffer();
    setupRecord(hashCode, dataOffset, buf);
    return buf;
  }

  protected byte[] getRecordBuffer() {
    return myBuffer;
  }

  protected void setupRecord(int hashCode, final int dataOffset, final byte[] buf) {
    Bits.putInt(buf, COLLISION_OFFSET, 0);
    Bits.putInt(buf, KEY_HASHCODE_OFFSET, hashCode);
    Bits.putInt(buf, KEY_REF_OFFSET, dataOffset);
  }

  public boolean iterateData(final Processor<Data> processor) throws IOException {
    flushKeysStream();

    DataInputStream keysStream = new DataInputStream(new BufferedInputStream(new FileInputStream(keystreamFile())));
    try {
      try {
        while (true) {
          Data key = myDataDescriptor.read(keysStream);
          if (!processor.process(key)) return false;
        }
      }
      catch (EOFException e) {
        // Done
      }
      return true;
    }
    finally {
      keysStream.close();
    }
  }

  private File keystreamFile() {
    return new File(myFile.getPath() + ".keystream");
  }

  private void flushKeysStream() {
    try {
      myKeyStream.flush();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private int hashCodeOf(int idx) throws IOException {
    return myStorage.getInt(idx + KEY_HASHCODE_OFFSET);
  }

  public synchronized Data valueOf(int idx) throws IOException {
    myKeyStream.flush();
    final MappedFile storage = myStorage;
    int addr = storage.getInt(idx + KEY_REF_OFFSET);
    myKeyReadStream.setup(addr, myKeyStream.size());
    return myDataDescriptor.read(myKeyReadStream);
  }

  private static class MyDataIS extends DataInputStream {
    private MyDataIS(RandomAccessFile raf) {
      super(new MyBufferedIS(new RandomAccessFileInputStream(raf, 0, 0)));
    }

    public void setup(long pos, long limit) {
      ((MyBufferedIS)in).setup(pos, limit);
    }
  }

  private static class MyBufferedIS extends BufferedInputStream {
    public MyBufferedIS(final InputStream in) {
      super(in, 512);
    }

    public void setup(long pos, long limit) {
      this.pos = 0;
      count = 0;
      ((RandomAccessFileInputStream)in).setup(pos, limit);
    }
  }

  public synchronized void close() throws IOException {
    if (!myClosed) {
      myClosed = true;
      try {
        myKeyStream.flush();
        myRaf.close();
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
      flushKeysStream();
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
}