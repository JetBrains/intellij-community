// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res.icon;

import com.pme.exe.res.LevelEntry;

/**
 * @author Sergey Zhulin
 * Date: Apr 28, 2006
 * Time: 11:38:20 AM
 */
public class GroupIconResourceDirectory extends LevelEntry {
  public GroupIconResourceDirectory() {
    super("Icon Directory");
    addMember( new Byte( "bWidth" ) );
    addMember( new Byte( "bHeight" ) );
    addMember( new Byte( "bColorCount" ) );
    addMember( new Byte( "bReserved" ) );
    addMember( new Word( "wPlanes" ) );
    addMember( new Word( "wBitCount" ) );
    addMember( new DWord( "dwBytesInRes" ) );
    addMember( new Word( "dwId" ) );
  }
}
