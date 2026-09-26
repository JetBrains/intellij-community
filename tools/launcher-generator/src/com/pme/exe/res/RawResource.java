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

/**
 * @author Sergey Zhulin
 * Date: Apr 25, 2006
 * Time: 3:12:58 PM
 */
public class RawResource extends LevelEntry {
  private final Bytes myRawResource;
  private final DWord myVirtualAddress;
  private final DWord mySize;
  private final Value myRawOffset;

  public RawResource(ResourceSectionReader section, DWord rva, DWord size) {
    super("Raw Resource");
    myVirtualAddress = rva;
    mySize = size;
    myRawOffset = new ValuesAdd(myVirtualAddress, section.getSectionVAtoRawOffset());
    myRawResource = new Bytes("Raw Resource Data", myRawOffset, size);
    myRawResource.addOffsetHolder(myRawOffset);
    myRawResource.addSizeHolder(size);
    addMember(myRawResource);
    addMember(new Padding("Resource Padding", 8));
  }
  public byte[] getBytes(){
    return myRawResource.getBytes();
  }
  public void setBytes( byte[] bytes ){
    myRawResource.setBytes(bytes);
  }

  public Value getRawOffset() {
    return myRawOffset;
  }

  @Override
  public String toString() {
    return "RawResource{" +
           "VirtualAddress=" + myVirtualAddress +
           ", RawOffset=" + myRawOffset +
           ", Size=" + mySize +
           '}';
  }
}
