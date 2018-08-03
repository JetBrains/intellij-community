// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res.icon;

import com.pme.exe.res.LevelEntry;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Zhulin
 * Date: Apr 27, 2006
 * Time: 1:13:15 PM
 */
public class IconDirectory extends LevelEntry {
  private RawBytes myRawBytes;
  public IconDirectory() {
    super("Icon Directory");
    addMember( new Byte( "bWidth" ) );
    addMember( new Byte( "bHeight" ) );
    addMember( new Byte( "bColorCount" ) );
    addMember( new Byte( "bReserved" ) );
    addMember( new Word( "wPlanes" ) );
    addMember( new Word( "wBitCount" ) );
    addMember( new DWord( "dwBytesInRes" ) );
    addMember( new DWord( "dwImageOffset" ) );
  }

  public byte[] getRawBytes(){
    Bytes bytes = (Bytes)myRawBytes.getMember( "Raw Bytes" );
    return bytes.getBytes();
  }

  public void read(DataInput stream) throws IOException {
    super.read(stream);
    RawBytes bytes = new RawBytes( getValueMember( "dwImageOffset" ),
        getValueMember( "dwBytesInRes" ));
    myRawBytes = bytes;
    getLevel().addLevelEntry( bytes );
  }
}
