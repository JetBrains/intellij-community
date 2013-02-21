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

/**
 * Date: Apr 25, 2006
 * Time: 3:12:58 PM
 */
public class RawResource extends LevelEntry {

  public RawResource(ResourceSectionReader section, DWord rva, DWord size) {
    super("Raw Resource");
    Value offsetHolder = new ValuesAdd(rva, section.getMainSectionsOffset());
    Bytes bytes = new Bytes("Raw Resource", offsetHolder, size);
    bytes.addOffsetHolder(offsetHolder);
    bytes.addSizeHolder(size);
    addMember(bytes);
  }
  public Bytes getBytes(){
    return (Bytes)getMember( "Raw Resource" );
  }
  public void setBytes( byte[] bytes ){
    Bytes mem = (Bytes) getMember("Raw Resource");
    mem.setBytes( bytes );
  }
}
