// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res;

import com.pme.exe.Bin;

import java.io.*;
import java.util.ArrayList;

/**
 * @author Sergey Zhulin
 * Date: Apr 6, 2006
 * Time: 11:22:12 PM
 */
public class DirectoryEntry extends LevelEntry {
  private ArrayOfBins<EntryDescription> myNamedEntries;
  private ArrayOfBins<EntryDescription> myIdEntries;
  private ResourceSectionReader mySection;
  private ArrayList<DirectoryEntry> mySubDirs = new ArrayList<DirectoryEntry>();
  private ArrayList<DataEntry> myDatas = new ArrayList<DataEntry>();
  private long myIdOrName;

  public DirectoryEntry(ResourceSectionReader section, EntryDescription entry, long idOrName) {
    super( createName( entry ) );
    myIdOrName = idOrName;
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

  public long getIdOrName() {
    return myIdOrName;
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

  public ArrayList<DirectoryEntry> getSubDirs() {
    return mySubDirs;
  }

  public DirectoryEntry findSubDir( String name ){
    for (DirectoryEntry directoryEntry : mySubDirs) {
      if (directoryEntry.getName().equals(name)) {
        return directoryEntry;
      }
    }
    return null;
  }

  public RawResource getRawResource(int index) {
    DataEntry dataEntry = myDatas.get(index);
    return dataEntry.getRawResource();
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
      myIdEntries = new ArrayOfBins<EntryDescription>("Id entries", EntryDescription.class, 0);
      addMember(myIdEntries);
      myIdEntries.setCountHolder(numberOfNamedEntries);
    }
    myIdEntries.addBin( entry );
  }

  public void read(DataInput stream) throws IOException {
    super.read(stream);

    Word numberOfNamedEntries = (Word) getMember("NumberOfNamedEntries");
    myNamedEntries = new ArrayOfBins<EntryDescription>("Named entries", EntryDescription.class, numberOfNamedEntries);
    addMember(myNamedEntries);
    myNamedEntries.setCountHolder(numberOfNamedEntries);
    myNamedEntries.read(stream);

    Word numberOfIdEntries = (Word) getMember("NumberOfIdEntries");
    myIdEntries = new ArrayOfBins<EntryDescription>("Id entries", EntryDescription.class, numberOfIdEntries);
    addMember(myIdEntries);
    myIdEntries.setCountHolder(numberOfIdEntries);
    myIdEntries.read(stream);

    processEntries(myNamedEntries);
    processEntries(myIdEntries);
  }

  private void processEntries(ArrayOfBins<EntryDescription> entries) {
    for (int i = 0; i < entries.size(); ++i) {
      EntryDescription entry = entries.get(i);
      Bin.Value offset = entry.getValueMember("OffsetToData");
      Bin.Value name = entry.getValueMember("Name");
      if ((offset.getValue() & 0x80000000) != 0) {
        addDirectoryEntry( new DirectoryEntry( mySection, entry, name.getValue()) );
      } else {
        addDataEntry( new DataEntry( mySection, offset) );
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
