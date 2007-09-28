/*
 * @author max
 */
package com.intellij.util.io.storage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.Forceable;
import com.intellij.util.io.RandomAccessDataFile;
import gnu.trove.TIntArrayList;

import java.io.File;
import java.io.IOException;

class RecordsTable implements Disposable, Forceable {
  private static final int FIRST_RECORD = 1;
  private static final int HEADER_MAGIC_OFFSET = 0;
  private static final int HEADER_VERSION_OFFSET = 4;
  private static final int CONNECTED_MAGIC = 0x12ad34e4;
  private static final int SAFELY_CLOSED_MAGIC = 0x1f2f3f4f;

  private static final int ADDRESS_OFFSET = 0;
  private static final int SIZE_OFFSET = ADDRESS_OFFSET + 8;
  private static final int RECORD_SIZE = SIZE_OFFSET + 4;
  private static final byte[] ZEROES = new byte[RECORD_SIZE];

  private final RandomAccessDataFile myStorage;

  private TIntArrayList myFreeRecordsList = null;
  private boolean myIsDirty = false;

  public RecordsTable(final File storageFilePath) throws IOException {
    myStorage = new RandomAccessDataFile(storageFilePath);
    if (myStorage.length() == 0) {
      cleanRecord(0); // Initialize header
      myIsDirty = true;
    }
    else {
      if (myStorage.getInt(HEADER_MAGIC_OFFSET) != SAFELY_CLOSED_MAGIC) {
        myStorage.dispose();
        throw new IOException("Records table for '" + storageFilePath + "' haven't been closed correctly. Rebuild required.");
      }
    }
  }

  private void markDirty() {
    if (!myIsDirty) {
      myStorage.putInt(HEADER_MAGIC_OFFSET, CONNECTED_MAGIC);
    }
  }

  public int createNewRecord() {
    markDirty();
    ensureFreeRecordsScanned();

    if (myFreeRecordsList.isEmpty()) {
      final int filelength = (int)myStorage.length();
      assert filelength % RECORD_SIZE == 0;

      int result = filelength / RECORD_SIZE;
      cleanRecord(result);

      return result;
    }
    else {
      return myFreeRecordsList.remove(myFreeRecordsList.size() - 1);
    }
  }

  private void ensureFreeRecordsScanned() {
    if (myFreeRecordsList == null) {
      myFreeRecordsList = scanForFreeRecords();
    }
  }

  private TIntArrayList scanForFreeRecords() {
    final int filelength = (int)myStorage.length();
    assert filelength % RECORD_SIZE == 0;

    final TIntArrayList result = new TIntArrayList();

    int lastRecord = filelength / RECORD_SIZE;
    for (int i = FIRST_RECORD; i < lastRecord; i++) {
      if (getAddress(i) == 0) {
        result.add(i);
      }
    }

    return result;
  }

  private void cleanRecord(int record) {
    myStorage.put(record * RECORD_SIZE, ZEROES, 0, RECORD_SIZE);
  }

  public long getAddress(int record) {
    return myStorage.getLong(record * RECORD_SIZE + ADDRESS_OFFSET);
  }

  public int getSize(int record) {
    return myStorage.getInt(record * RECORD_SIZE + SIZE_OFFSET);
  }

  public void setAddress(int record, long address) {
    markDirty();
    myStorage.putLong(record * RECORD_SIZE + ADDRESS_OFFSET, address);
  }

  public void setSize(int record, int size) {
    markDirty();
    myStorage.putInt(record * RECORD_SIZE + SIZE_OFFSET, size);
  }

  public void deleteRecord(final int record) {
    ensureFreeRecordsScanned();

    cleanRecord(record);
    myFreeRecordsList.add(record);
  }

  public int getVersion() {
    return myStorage.getInt(HEADER_VERSION_OFFSET);
  }

  public void setVersion(final int expectedVersion) {
    markDirty();
    myStorage.putInt(HEADER_VERSION_OFFSET, expectedVersion);
  }

  public void dispose() {
    markClean();
    myStorage.dispose();
  }

  public void force() {
    markClean();
    myStorage.force();
  }

  public boolean isDirty() {
    return myIsDirty;
  }

  private void markClean() {
    if (myIsDirty) {
      myIsDirty = true;
      myStorage.putInt(HEADER_MAGIC_OFFSET, SAFELY_CLOSED_MAGIC);
    }
  }

  public int getRecordsCount() {
    return (int)(myStorage.length() / RECORD_SIZE);
  }
}