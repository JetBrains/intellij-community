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

/*
 * @author max
 */
package com.intellij.util.io.storage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.RecordDataOutput;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class Storage implements Disposable, Forceable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.storage.Storage");

  private final Object lock = new Object();
  private final RecordsTable myRecordsTable;
  private DataTable myDataTable;

  private static final int CACHE_SIZE = 8192;
  private final static Map<AppenderCacheKey, AppenderStream> ourAppendersCache = Collections.synchronizedMap(new LinkedHashMap<AppenderCacheKey, AppenderStream>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(final Map.Entry<AppenderCacheKey, AppenderStream> eldest) {
      if (size() < CACHE_SIZE) return false;

      closeStream(eldest.getValue());
      return true;
    }
  });

  private static final ThreadLocal<AppenderCacheKey> KEY_FLYWEIGHT = new ThreadLocal<AppenderCacheKey>() {
    @Override
    protected AppenderCacheKey initialValue() {
      return new AppenderCacheKey(0, null);
    }
  };

  private static class AppenderCacheKey {
    public Storage storage;
    public int record;

    public AppenderCacheKey(final int record, final Storage storage) {
      this.record = record;
      this.storage = storage;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof AppenderCacheKey)) return false;

      final AppenderCacheKey that = (AppenderCacheKey)o;

      if (record != that.record) return false;
      if (!storage.equals(that.storage)) return false;

      return true;
    }

    public int hashCode() {
      return record;
    }
  }

  public static boolean deleteFiles(String storageFilePath) {
    final File recordsFile = new File(storageFilePath + ".rindex");
    final File dataFile = new File(storageFilePath + ".data");

    return FileUtil.delete(recordsFile) && FileUtil.delete(dataFile);
  }

  @NotNull
  public static Storage create(String storageFilePath) throws IOException {
    final File recordsFile = new File(storageFilePath + ".rindex");
    final File dataFile = new File(storageFilePath + ".data");

    if (recordsFile.exists() != dataFile.exists()) {
      deleteFiles(storageFilePath);
    }

    if (!recordsFile.exists()) {
      recordsFile.getParentFile().mkdirs();

      recordsFile.createNewFile();
      dataFile.createNewFile();
    }

    RecordsTable recordsTable = null;
    DataTable dataTable;
    try {
      recordsTable = new RecordsTable(recordsFile);
      dataTable = new DataTable(dataFile);
    }
    catch (IOException e) {
      LOG.info(e.getMessage());
      if (recordsTable != null) {
        recordsTable.dispose();
      }

      boolean deleted = deleteFiles(storageFilePath);
      if (!deleted) {
        throw new IOException("Can't delete caches at: " + storageFilePath);
      }
      return create(storageFilePath);
    }

    return new Storage(storageFilePath, recordsTable, dataTable);
  }

  private Storage(String path, RecordsTable recordsTable, DataTable dataTable) {
    myRecordsTable = recordsTable;
    myDataTable = dataTable;

    if (myDataTable.isCompactNecessary()) {
      compact(path);
    }
  }

  public int getRecordsCount() {
    return myRecordsTable.getRecordsCount();
  }

  private void compact(final String path) {
    synchronized (lock) {
      LOG.info("Space waste in " + path + " is " + myDataTable.getWaste() + " bytes. Compacting now.");
      long start = System.currentTimeMillis();

      try {
        File newDataFile = new File(path + ".data.temp");
        FileUtil.delete(newDataFile);
        newDataFile.createNewFile();

        File oldDataFile = new File(path + ".data");
        DataTable newDataTable = new DataTable(newDataFile);

        final int count = myRecordsTable.getRecordsCount();
        for (int i = 0; i < count; i++) {
          final long addr = myRecordsTable.getAddress(i);
          final int size = myRecordsTable.getSize(i);

          if (addr != 0 && size != 0) {
            final int capacity = calcCapacity(size);
            final long newaddr = newDataTable.allocateSpace(capacity);
            final byte[] bytes = new byte[size];
            myDataTable.readBytes(addr, bytes);
            newDataTable.writeBytes(newaddr, bytes);
            myRecordsTable.setAddress(i, newaddr);
            myRecordsTable.setCapacity(i, capacity);
          }
        }

        myDataTable.dispose();
        newDataTable.dispose();

        if (!FileUtil.delete(oldDataFile)) {
          throw new IOException("Can't delete file: " + oldDataFile);
        }

        newDataFile.renameTo(oldDataFile);
        myDataTable = new DataTable(oldDataFile);
      }
      catch (IOException e) {
        LOG.info("Compact failed: " + e.getMessage());
      }

      long timedelta = System.currentTimeMillis() - start;
      LOG.info("Done compacting in " + timedelta + "msec.");
    }
  }

  public int getVersion() {
    synchronized (lock) {
      return myRecordsTable.getVersion();
    }
  }

  public void setVersion(int expectedVersion) {
    synchronized (lock) {
      myRecordsTable.setVersion(expectedVersion);
    }
  }

  public void force() {
    synchronized (lock) {
      for (AppenderCacheKey key : new ArrayList<AppenderCacheKey>(ourAppendersCache.keySet())) {
        AppenderStream stream = ourAppendersCache.get(key);
        if (stream != null && stream.myStorage == this) {
          try {
            stream.realClose();
          }
          catch (IOException e) {
            LOG.error(e);
          }
          ourAppendersCache.remove(key);
        }
      }

      myDataTable.force();
      myRecordsTable.force();
    }
  }

  public boolean isDirty() {
    synchronized (lock) {
      return myDataTable.isDirty() || myRecordsTable.isDirty();
    }
  }

  public int createNewRecord() {
    synchronized (lock) {
      return myRecordsTable.createNewRecord();
    }
  }

  public StorageDataOutput createStream() {
    return writeStream(createNewRecord());
  }

  public void appendBytes(int record, byte[] bytes) {
    int delta = bytes.length;
    if (delta == 0) return;

    synchronized (lock) {
      int capacity = myRecordsTable.getCapacity(record);
      int oldSize = myRecordsTable.getSize(record);
      int newSize = oldSize + delta;
      if (newSize > capacity) {
        byte[] newbytes = new byte[newSize];
        System.arraycopy(doReadBytes(record), 0, newbytes, 0, oldSize);
        System.arraycopy(bytes, 0, newbytes, oldSize, delta);
        doWriteBytes(record, newbytes);
      }
      else {
        long address = myRecordsTable.getAddress(record) + oldSize;
        myDataTable.writeBytes(address, bytes);
        myRecordsTable.setSize(record, newSize);
      }
    }
  }

  public void writeBytes(int record, byte[] bytes) {
    dropRecordCache(record);
    doWriteBytes(record, bytes);
  }

  private void doWriteBytes(final int record, final byte[] bytes) {
    synchronized (lock) {
      final int requiredLength = bytes.length;
      final int currentCapacity = myRecordsTable.getCapacity(record);

      if (requiredLength == 0 && myRecordsTable.getSize(record) == 0) return;

      final long address;
      if (currentCapacity >= requiredLength) {
        address = myRecordsTable.getAddress(record);
      }
      else {
        if (currentCapacity > 0) {
          myDataTable.reclaimSpace(currentCapacity);
        }

        final int newCapacity = calcCapacity(requiredLength);
        address = myDataTable.allocateSpace(newCapacity);
        myRecordsTable.setAddress(record, address);
        myRecordsTable.setCapacity(record, newCapacity);
      }

      myDataTable.writeBytes(address, bytes);
      myRecordsTable.setSize(record, requiredLength);
    }
  }

  private void dropRecordCache(final int record) {
    final AppenderCacheKey key = setupKey(record, this);
    final AppenderStream stream = ourAppendersCache.get(key);
    if (stream != null) {
      closeStream(stream);
      ourAppendersCache.remove(key);
    }
  }

  private static AppenderCacheKey setupKey(int record, Storage storage) {
    AppenderCacheKey key = KEY_FLYWEIGHT.get();
    key.storage = storage;
    key.record = record;
    return key;
  }

  private static int calcCapacity(int requiredLength) {
    if (requiredLength < 2048) {
      int capacity = 1;
      while (requiredLength != 0) {
        capacity *= 2;
        requiredLength /= 2;
      }
      return capacity;
    }
    else {
      return requiredLength * 3 / 2;
    }
  }
  
  public StorageDataOutput writeStream(final int record) {
    return new StorageDataOutput(this, record);
  }

  public AppenderStream appendStream(int record) {
    AppenderStream appenderStream = ourAppendersCache.get(setupKey(record, this));
    if (appenderStream != null) {
      if (appenderStream.size() > 4048) {
        closeStream(appenderStream);
      }
      else {
        return appenderStream;
      }
    }

    appenderStream = new AppenderStream(this, record);

    ourAppendersCache.put(new AppenderCacheKey(record, this), appenderStream);
    return appenderStream;
  }

  private static void closeStream(final AppenderStream appenderStream) {
    try {
      appenderStream.realClose();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public DataInputStream readStream(int record) {
    final byte[] bytes = readBytes(record);
    return new DataInputStream(new ByteArrayInputStream(bytes));
  }

  public byte[] readBytes(int record) {
    dropRecordCache(record);
    return doReadBytes(record);
  }

  private byte[] doReadBytes(final int record) {
    synchronized (lock) {
      final int length = myRecordsTable.getSize(record);
      if (length == 0) return ArrayUtil.EMPTY_BYTE_ARRAY;

      final long address = myRecordsTable.getAddress(record);
      byte[] result = new byte[length];
      myDataTable.readBytes(address, result);

      return result;
    }
  }

  public void deleteRecord(int record) {
    dropRecordCache(record);
    synchronized (lock) {
      myRecordsTable.deleteRecord(record);
    }
  }

  public void dispose() {
    synchronized (lock) {
      myRecordsTable.dispose();
      myDataTable.dispose();
    }
  }

  public static class StorageDataOutput extends DataOutputStream implements RecordDataOutput {
    private final Storage myStorage;
    private final int myRecordId;

    public StorageDataOutput(Storage storage, int recordId) {
      super(new ByteArrayOutputStream());
      myStorage = storage;
      myRecordId = recordId;
    }

    public void close() throws IOException {
      super.close();
      myStorage.writeBytes(myRecordId, ((ByteArrayOutputStream)out).toByteArray());
    }

    public int getRecordId() {
      return myRecordId;
    }
  }

  public static class AppenderStream extends DataOutputStream implements RecordDataOutput {
    private final Storage myStorage;
    private final int myRecordId;

    public AppenderStream(Storage storage, int recordId) {
      super(new ByteArrayOutputStream());
      myStorage = storage;
      myRecordId = recordId;
    }

    public void close() throws IOException {
    }

    public int getRecordId() {
      return myRecordId;
    }

    public void realClose() throws IOException {
      super.close();
      myStorage.appendBytes(myRecordId, ((ByteArrayOutputStream)out).toByteArray());
    }
  }
}