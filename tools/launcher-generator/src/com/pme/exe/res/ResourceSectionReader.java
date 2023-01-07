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
import com.pme.exe.ImageOptionalHeader;
import com.pme.exe.ImageSectionHeader;
import com.pme.exe.Section;

import java.util.Iterator;

/**
 * @author Sergey Zhulin
 * Date: Apr 6, 2006
 * Time: 7:10:09 PM
 */
public class ResourceSectionReader extends Section {
  private final Level myRoot = new Level(0);
  private final Bin.DWord myStartOffset;
  private final Bin.DWord mySectionVAtoRawOffset;

  public ResourceSectionReader(ImageSectionHeader header, ImageOptionalHeader imageOptionalHeader) {
    super(header, header.getSectionName());
    myStartOffset = header.getPointerToRawData();
    mySectionVAtoRawOffset = new ValuesAdd(header.getVirtualAddress(), myStartOffset);

    myRoot.addLevelEntry(new DirectoryEntry(this, null, 0));
    addMember(myRoot);
    addMember(new Padding("FileAlignment in ResourceSection", (int)imageOptionalHeader.getFileAlignment().getValue()));
    addSizeHolder(header.getVirtualSize());
  }

  public DirectoryEntry getRoot() {
    Iterator<Bin> iterator = myRoot.getMembers().iterator();
    DirectoryEntry entry = (DirectoryEntry)iterator.next();
    assert !iterator.hasNext();
    return entry;
  }

  public DWord getStartOffset() {
    return myStartOffset;
  }

  public DWord getSectionVAtoRawOffset() {
    return mySectionVAtoRawOffset;
  }
}
