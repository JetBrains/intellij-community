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
import java.util.concurrent.locks.ReentrantLock;

public class Storage implements Disposable, Forceable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.storage.Storage");

  private final ReentrantLock lock = new ReentrantLock();
  private final RecordsTable myRecordsTable;
  private DataTable myDataTable;

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

  public Storage(String path, RecordsTable recordsTable, DataTable dataTable) {
    myRecordsTable = recordsTable;
    myDataTable = dataTable;

    if (myDataTable.isCompactNecessary()) {
      compact(path);
    }
  }

  private void compact(final String path) {
    LOG.info("Space waste in " + path + " is " + myDataTable.getWaste() + " bytes. Compacting now.");
    long start = System.currentTimeMillis();

    lock.lock();
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
          final long newaddr = newDataTable.allocateSpace(size);
          final byte[] bytes = new byte[size];
          myDataTable.readBytes(addr, bytes);
          newDataTable.writeBytes(newaddr, bytes);
          myRecordsTable.setAddress(i, newaddr);
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
    finally {
      lock.unlock();
    }

    long timedelta = System.currentTimeMillis() - start;
    LOG.info("Done compacting in " + timedelta + "msec.");
  }

  public int getVersion() {
    lock.lock();
    try {
      return myRecordsTable.getVersion();
    }
    finally {
      lock.unlock();
    }
  }

  public void setVersion(int expectedVersion) {
    lock.lock();
    try {
      myRecordsTable.setVersion(expectedVersion);
    }
    finally {
      lock.unlock();
    }
  }

  public void force() {
    lock.lock();
    try {
      myDataTable.force();
      myRecordsTable.force();
    }
    finally {
      lock.unlock();
    }
  }

  public boolean isDirty() {
    lock.lock();
    try {
      return myDataTable.isDirty() || myRecordsTable.isDirty();
    }
    finally {
      lock.unlock();
    }
  }

  public int createNewRecord() {
    lock.lock();
    try {
      return myRecordsTable.createNewRecord();
    }
    finally {
      lock.unlock();
    }
  }

  public StorageDataOutput createStream() {
    return writeStream(createNewRecord());
  }

  public void writeBytes(int record, byte[] bytes) {
    lock.lock();
    try {
      final int requiredLength = bytes.length;
      final int currentSize = myRecordsTable.getSize(record);

      if (requiredLength == currentSize && currentSize == 0) return;

      final long address;
      if (currentSize >= requiredLength) {
        address = myRecordsTable.getAddress(record);
      }
      else {
        address = myDataTable.allocateSpace(requiredLength);
        myRecordsTable.setAddress(record, address);
      }

      myDataTable.writeBytes(address, bytes);
      myRecordsTable.setSize(record, requiredLength);
    }
    finally {
      lock.unlock();
    }
  }

  public StorageDataOutput writeStream(final int record) {
    return new StorageDataOutput(this, record);
  }

  public DataInputStream readStream(int record) {
    final byte[] bytes = readBytes(record);
    return new DataInputStream(new ByteArrayInputStream(bytes));
  }

  public byte[] readBytes(int record) {
    lock.lock();
    try {
      final int length = myRecordsTable.getSize(record);
      if (length == 0) return ArrayUtil.EMPTY_BYTE_ARRAY;

      final long address = myRecordsTable.getAddress(record);
      byte[] result = new byte[length];
      myDataTable.readBytes(address, result);

      return result;
    }
    finally {
      lock.unlock();
    }
  }

  public void deleteRecord(int record) {
    lock.lock();
    try {
      final int length = myRecordsTable.getSize(record);
      if (length != 0) {
        final long address = myRecordsTable.getAddress(record);
        myDataTable.reclaimSpace(address, length);
      }

      myRecordsTable.deleteRecord(record);
    }
    finally {
      lock.unlock();
    }
  }

  public void dispose() {
    lock.lock();
    try {
      myRecordsTable.dispose();
      myDataTable.dispose();
    }
    finally {
      lock.unlock();
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
}