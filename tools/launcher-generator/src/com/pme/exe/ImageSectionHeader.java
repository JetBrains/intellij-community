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

package com.pme.exe;

/**
 * @author Sergey Zhulin
 * Date: Apr 1, 2006
 * Time: 1:54:57 PM
 */
public class ImageSectionHeader extends Bin.Structure {
  private final CharStringFS myNameTxt;
  private final DWord myVirtualSize;
  private final DWord myVirtualAddress;
  private final DWord mySizeOfRawData;
  private final DWord myPointerToRawData;

  public ImageSectionHeader() {
    super("Image section header");
    addMember(myNameTxt = new CharStringFS("Name", 8));
    addMember(myVirtualSize = new DWord("VirtualSize"));
    addMember(myVirtualAddress = new DWord("VirtualAddress"));
    addMember(mySizeOfRawData = new DWord("SizeOfRawData"));
    addMember(myPointerToRawData = new DWord("PointerToRawData"));
    addMember(new DWord("PointerToRelocations"));
    addMember(new DWord("PointerToLineNumbers"));
    addMember(new Word("NumberOfRelocations"));
    addMember(new Word("NumberOfLineNumbers"));
    addMember(new DWord("Characteristics"));
  }

  public String getSectionName() {
    return myNameTxt.getValue();
  }

  public DWord getVirtualSize() {
    return myVirtualSize;
  }

  public DWord getVirtualAddress() {
    return myVirtualAddress;
  }

  public DWord getSizeOfRawData() {
    return mySizeOfRawData;
  }

  public DWord getPointerToRawData() {
    return myPointerToRawData;
  }
}
