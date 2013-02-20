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

import com.pme.exe.Bin;

import java.io.*;
import java.util.ArrayList;

/**
 * Date: Apr 6, 2006
 * Time: 11:22:12 PM
 */
public class DirectoryEntry extends LevelEntry {
  private ArrayOfBins myNamedEntries;
  private ArrayOfBins myIdEntries;
  private ResourceSectionReader mySection;
  private ArrayList mySubDirs = new ArrayList();
  private ArrayList myDatas = new ArrayList();

  public DirectoryEntry( ResourceSectionReader section, EntryDescription entry ) {
    super( createName( entry ) );
    addMember(new DWord("Characteristics"));
    addMember(new DWord("TimeDateStamp"));
    addMember(new Word("MajorVersion"));
    addMember(new Word("MinorVersion"));
    addMember(new Word("NumberOfNamedEntries"));
    addMember(new Word("NumberOfIdEntries"));
    mySection = section;
    if ( entry != null ){
      Value flagValue = new DWord( "flag" ).setValue( 0x80000000 );
      Bin.Value offset = entry.getValueMember("OffsetToData");
      addOffsetHolder( new ValuesSub( offset, new ValuesAdd( mySection.getStartOffset(), flagValue ) ));
    }
  }

  public ResourceSectionReader getSection(){
    return mySection;
  }

  public static String createName( EntryDescription entry ){
    String name = "IRD";
    if ( entry != null ){
      name += entry.getValueMember( "Name" ).getValue();
    }
    return name;
  }

  public DirectoryEntry findSubDir( String name ){
    for (int i = 0; i < mySubDirs.size(); i++) {
      DirectoryEntry directoryEntry = (DirectoryEntry) mySubDirs.get(i);
      if ( directoryEntry.getName().equals( name ) ){
        return directoryEntry;
      }
    }
    return null;
  }

  public ArrayList getSubDirs(){
    return mySubDirs;
  }

  public RawResource getRawResource( int index ){
    DataEntry dataEntry = (DataEntry)myDatas.get(index);
    return dataEntry.getRawResource();
  }
  public ArrayList getDatas() {
    return myDatas;
  }

  public long sizeInBytes() {
    return super.sizeInBytes();
  }

  public void insertDataEntry( int index, DataEntry dataEntry ){
    myDatas.add( dataEntry );
    getLevel().insertLevelEntry( index, dataEntry );
  }

  public void addDataEntry( DataEntry dataEntry ){
    myDatas.add( dataEntry );
    getLevel().addLevelEntry( dataEntry );
  }

  public void insertDirectoryEntry( int index, DirectoryEntry dir ){
    getLevel().insertLevelEntry( index, dir );
    mySubDirs.add( dir );
  }

  public void addDirectoryEntry( DirectoryEntry dir ){
    getLevel().addLevelEntry( dir );
    mySubDirs.add( dir );
  }
  public void addIdEntry( EntryDescription entry ){
    if ( myIdEntries == null ){
      Word numberOfNamedEntries = (Word) getMember("NumberOfIdEntries");
      myIdEntries = new ArrayOfBins("Id entries", EntryDescription.class, 0);
      addMember(myIdEntries);
      myIdEntries.setCountHolder(numberOfNamedEntries);
    }
    myIdEntries.addBin( entry );
  }

  public void read(DataInput stream) throws IOException {
    super.read(stream);

    Word numberOfNamedEntries = (Word) getMember("NumberOfNamedEntries");
    myNamedEntries = new ArrayOfBins("Named entries", EntryDescription.class, numberOfNamedEntries);
    addMember(myNamedEntries);
    myNamedEntries.setCountHolder(numberOfNamedEntries);
    myNamedEntries.read(stream);

    Word numberOfIdEntries = (Word) getMember("NumberOfIdEntries");
    myIdEntries = new ArrayOfBins("Id entries", EntryDescription.class, numberOfIdEntries);
    addMember(myIdEntries);
    myIdEntries.setCountHolder(numberOfIdEntries);
    myIdEntries.read(stream);

    processEntries(myNamedEntries);
    processEntries(myIdEntries);
  }

  private void processEntries(ArrayOfBins entries) {
    for (int i = 0; i < entries.size(); ++i) {
      EntryDescription entry = (EntryDescription) entries.get(i);
      Bin.Value offset = entry.getValueMember("OffsetToData");
      if ((offset.getValue() & 0x80000000) != 0) {
        addDirectoryEntry( new DirectoryEntry( mySection, entry ) );
      } else {
        addDataEntry( new DataEntry( mySection, offset ) );
      }
    }
  }

  public void report(OutputStreamWriter writer) throws IOException {
    super.report(writer);
  }

  public void write(DataOutput stream) throws IOException {
    super.write(stream);
  }

}
