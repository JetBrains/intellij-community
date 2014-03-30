/*
 * Copyright 2006 ProductiveMe Inc.
 * Copyright 2013 JetBrains s.r.o.
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

package com.pme.exe.res;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class StringTableDirectory {
  private static class Entry {
    int startID;
    StringTable table;
    RawResource resource;
  }

  private final List<Entry> myEntries = new ArrayList<Entry>();

  public StringTableDirectory(DirectoryEntry directoryEntry) throws IOException {
    for (DirectoryEntry subDir : directoryEntry.getSubDirs()) {
      Entry e = new Entry();
      e.startID = (int) subDir.getIdOrName();
      e.resource = subDir.getRawResource(0);
      e.table = new StringTable(e.resource.getBytes().getBytes());
      myEntries.add(e);
    }
  }

  public void setString(int id, String value) {
    boolean found = false;
    for (Entry entry : myEntries) {
      if (entry.startID == (id / 16)+1) {
        entry.table.setString(id % 16, value);
        found = true;
        break;
      }
    }
    if (!found) {
      throw new IllegalArgumentException("Cannot find string entry with ID " + id);
    }
  }

  public void save() throws IOException {
    for (Entry entry : myEntries) {
      entry.resource.setBytes(entry.table.getBytes());
    }
  }
}
