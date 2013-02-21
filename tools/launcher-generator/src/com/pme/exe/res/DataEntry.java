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

import java.io.IOException;
import java.io.DataInput;
import java.io.DataOutput;

/**
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
