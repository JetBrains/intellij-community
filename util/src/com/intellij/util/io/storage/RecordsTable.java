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
import com.intellij.util.io.PagePool;
import com.intellij.util.io.RandomAccessDataFile;
import gnu.trove.TIntArrayList;

import java.io.File;
import java.io.IOException;

class RecordsTable implements Disposable, Forceable {
  private static final int FIRST_RECORD = 1;
  private static final int HEADER_MAGIC_OFFSET = 0;
  private static final int HEADER_VERSION_OFFSET = 4;
  private static final int CONNECTED_MAGIC = 0x12ad34e4;
  private static final int VERSION = 3;
  private static final int SAFELY_CLOSED_MAGIC = 0x1f2f3f4f + VERSION;

  private static final int ADDRESS_OFFSET = 0;
  private static final int SIZE_OFFSET = ADDRESS_OFFSET + 8;
  private static final int CAPACITY_OFFSET = SIZE_OFFSET + 4;
  private static final int RECORD_SIZE = CAPACITY_OFFSET + 4;
  private static final byte[] ZEROES = new byte[RECORD_SIZE];

  private final RandomAccessDataFile myStorage;

  private TIntArrayList myFreeRecordsList = null;
  private boolean myIsDirty = false;

  public RecordsTable(final File storageFilePath, final PagePool pool) throws IOException {
    myStorage = new RandomAccessDataFile(storageFilePath, pool);
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

  public void markDirty() {
    if (!myIsDirty) {
      myIsDirty = true;
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
      final int result = myFreeRecordsList.remove(myFreeRecordsList.size() - 1);
      assert getSize(result) == -1;
      setSize(result, 0);
      return result;
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
      if (getSize(i) == -1) {
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

  public int getCapacity(int record) {
    return myStorage.getInt(record * RECORD_SIZE + CAPACITY_OFFSET);
  }

  public void setAddress(int record, long address) {
    markDirty();
    myStorage.putLong(record * RECORD_SIZE + ADDRESS_OFFSET, address);
  }

  public void setCapacity(int record, int capacity) {
    markDirty();
    myStorage.putInt(record * RECORD_SIZE + CAPACITY_OFFSET, capacity);
  }

  public void setSize(int record, int size) {
    markDirty();
    myStorage.putInt(record * RECORD_SIZE + SIZE_OFFSET, size);
  }

  public void deleteRecord(final int record) {
    ensureFreeRecordsScanned();
    setSize(record, -1);
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

  public boolean flushSome(int maxPages) {
    myStorage.flushSomePages(maxPages);
    if (!myStorage.isDirty()) {
      force();
      return true;
    }
    return false;
  }

  public boolean isDirty() {
    return myIsDirty;
  }

  private void markClean() {
    if (myIsDirty) {
      myIsDirty = false;
      myStorage.putInt(HEADER_MAGIC_OFFSET, SAFELY_CLOSED_MAGIC);
    }
  }

  public int getRecordsCount() {
    return (int)(myStorage.length() / RECORD_SIZE);
  }
}
