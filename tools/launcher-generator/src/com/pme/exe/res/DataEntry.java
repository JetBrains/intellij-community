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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Sergey Zhulin
 * Date: Apr 20, 2006
 * Time: 3:58:05 PM
 */
public class DataEntry extends LevelEntry {
  private final DWord myRVA;
  private final DWord mySize;
  private final RawResource myRawResource;
  private final DWord myLanguage;

  public DataEntry(ResourceSectionReader section, DWord offset, DWord language) {
    super("DataEntry");
    myLanguage = language;
    addMember(myRVA = new DWord("RVA"));
    addMember(mySize = new DWord("Size"));
    addMember(new DWord("Code Page"));
    addMember(new DWord("Reserved"));
    addOffsetHolder(new ValuesSub(offset, section.getStartOffset()));
    myRawResource = new RawResource(section, myRVA, mySize);
  }

  public RawResource getRawResource() {
    return myRawResource;
  }

  public DWord getLanguage() {
    return myLanguage;
  }

  public void initRawData(){
    getLevel().addLevelEntry(myRawResource);
  }

  @Override
  public void read(DataInput stream) throws IOException {
    super.read(stream);
    initRawData();
  }

  @Override
  public String toString() {
    return "DataEntry{" +
           "RVA=" + myRVA +
           ", Size=" + mySize +
           '}';
  }
}
