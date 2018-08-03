// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res;

/**
 * @author Sergey Zhulin
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
