// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class StringTableDirectory {
  private static class Entry {
    int startID;
    StringTable table;
    RawResource resource;
  }

  private final List<Entry> myEntries = new ArrayList<>();

  public StringTableDirectory(DirectoryEntry directoryEntry) throws IOException {
    for (DirectoryEntry subDir : directoryEntry.getSubDirs()) {
      Entry e = new Entry();
      e.startID = (int) subDir.getIdOrName();
      e.resource = subDir.getRawResource();
      e.table = new StringTable(e.resource.getBytes());
      myEntries.add(e);
    }
  }

  public void setString(int id, String value) {
    for (Entry entry : myEntries) {
      if (entry.startID == (id / 16)+1) {
        entry.table.setString(id % 16, value);
        return;
      }
    }
    throw new IllegalArgumentException("Cannot find string entry with ID " + id);
  }

  public void save() throws IOException {
    for (Entry entry : myEntries) {
      entry.resource.setBytes(entry.table.getBytes());
    }
  }
}
