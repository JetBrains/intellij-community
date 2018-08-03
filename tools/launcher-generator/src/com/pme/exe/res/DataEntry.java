// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res;

import com.pme.exe.Bin;

import java.io.IOException;
import java.io.DataInput;
import java.io.DataOutput;

/**
 * @author Sergey Zhulin
 * Date: Apr 20, 2006
 * Time: 3:58:05 PM
 */
public class DataEntry extends LevelEntry {
  private ResourceSectionReader mySection;
  private RawResource myRawResource = null;
  public DataEntry( ResourceSectionReader section, Bin.Value offsetHolder ) {
    super("DataEntry");
    addMember(new DWord("RVA"));
    addMember(new DWord("Size"));
    addMember(new DWord("Code Page"));
    addMember(new DWord("Reserved"));
    mySection = section;
    addOffsetHolder( new ValuesSub( offsetHolder, mySection.getStartOffset() ) );
  }

  public RawResource getRawResource() {
    return myRawResource;
  }

  public void initRawData(){
    myRawResource = new RawResource( mySection, (DWord) getValueMember("RVA"), (DWord) getValueMember("Size"));
    getLevel().addLevelEntry( myRawResource );
  }
  public void insertRawData( int index ){
    myRawResource = new RawResource( mySection, (DWord) getValueMember("RVA"), (DWord) getValueMember("Size"));
    getLevel().insertLevelEntry( index, myRawResource );
  }

  public void read(DataInput stream) throws IOException {
    super.read(stream);
    initRawData();
  }

  public void write(DataOutput stream) throws IOException {
    super.write(stream);
  }
}
