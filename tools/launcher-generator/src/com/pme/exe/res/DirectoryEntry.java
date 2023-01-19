/*
 * Copyright 2006 ProductiveMe Inc.
 * Copyright 2013-2022 JetBrains s.r.o.
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

import com.pme.exe.Bin;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author Sergey Zhulin
 * Date: Apr 6, 2006
 * Time: 11:22:12 PM
 */
public class DirectoryEntry extends LevelEntry {
  private static final long DIRECTORY_ENTRY_FLAG = 0xffff_ffff_8000_0000L;
  public static final int RT_BITMAP = 2;
  public static final int RT_ICON = 3;
  public static final int RT_STRING = 6;
  public static final int RT_GROUP_ICON = RT_ICON + 11;
  public static final int RT_VERSION = 16;

  private final ArrayOfBins<EntryDescription> myNamedEntries;
  private final ArrayOfBins<EntryDescription> myIdEntries;
  private final ResourceSectionReader mySection;
  private final ArrayList<DirectoryEntry> mySubDirs = new ArrayList<>();
  private final ArrayList<DataEntry> myData = new ArrayList<>();
  private final long myIdOrName;

  public DirectoryEntry(ResourceSectionReader section, EntryDescription entry, long idOrName) {
    super(String.valueOf(idOrName));
    myIdOrName = idOrName;
    addMember(new DWord("Characteristics"));
    addMember(new DWord("TimeDateStamp"));
    addMember(new Word("MajorVersion"));
    addMember(new Word("MinorVersion"));
    Word numberOfNamedEntries = addMember(new Word("NumberOfNamedEntries"));
    Word numberOfIdEntries = addMember(new Word("NumberOfIdEntries"));
    mySection = section;
    if (entry != null) {
      Value flagValue = new DWord("flag").setValue(DIRECTORY_ENTRY_FLAG);
      Bin.Value offset = entry.getOffsetToData();
      addOffsetHolder(new ValuesSub(offset, new ValuesAdd(mySection.getStartOffset(), flagValue)));
    }
    myNamedEntries = addMember(new ArrayOfBins<>("Named entries", EntryDescription.class, numberOfNamedEntries));
    myNamedEntries.setCountHolder(numberOfNamedEntries);

    myIdEntries = addMember(new ArrayOfBins<>("Id entries", EntryDescription.class, numberOfIdEntries));
    myIdEntries.setCountHolder(numberOfIdEntries);
  }

  public long getIdOrName() {
    return myIdOrName;
  }

  public ResourceSectionReader getSection(){
    return mySection;
  }

  public ArrayList<DirectoryEntry> getSubDirs() {
    return mySubDirs;
  }

  public ArrayList<DataEntry> getData() {
    return myData;
  }

  public DirectoryEntry findSubDir(long id) {
    for (DirectoryEntry directoryEntry : mySubDirs) {
      if (directoryEntry.getIdOrName() == id) {
        return directoryEntry;
      }
    }
    return null;
  }

  public RawResource getRawResource() {
    Iterator<DataEntry> iterator = myData.iterator();
    assert iterator.hasNext();
    DataEntry dataEntry = iterator.next();
    assert !iterator.hasNext();
    return dataEntry.getRawResource();
  }

  public void addDataEntry(DataEntry dataEntry) {
    getLevel().addLevelEntry(dataEntry);
    myData.add(dataEntry);
  }

  public void addDirectoryEntry(DirectoryEntry dir) {
    getLevel().addLevelEntry(dir);
    mySubDirs.add(dir);
  }

  public void addIdEntry(EntryDescription entry) {
    myIdEntries.addBin(entry);
  }

  @Override
  public void read(DataInput stream) throws IOException {
    super.read(stream);

    processEntries(myNamedEntries);
    processEntries(myIdEntries);
  }

  private void processEntries(ArrayOfBins<EntryDescription> entries) {
    for (EntryDescription entry : entries) {
      DWord offset = entry.getOffsetToData();
      DWord name = entry.getNameW();
      if ((offset.getValue() & DIRECTORY_ENTRY_FLAG) != 0) {
        addDirectoryEntry(new DirectoryEntry(mySection, entry, name.getValue()));
      }
      else {
        addDataEntry(new DataEntry(mySection, offset, name));
      }
    }
  }

  @Override
  public String toString() {
    return "DirectoryEntry{" +
           ", idOrName=" + myIdOrName +
           ", subs=" + mySubDirs +
           ", data=" + myData +
           '}';
  }
}
