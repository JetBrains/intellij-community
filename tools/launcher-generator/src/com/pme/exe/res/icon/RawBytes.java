// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res.icon;

import com.pme.exe.res.LevelEntry;
import com.pme.exe.Bin;

/**
 * @author Sergey Zhulin
 * Date: Apr 27, 2006
 * Time: 1:44:11 PM
 */
public class RawBytes extends LevelEntry {
  public RawBytes( Bin.Value offsetHolder, Bin.Value size ) {
    super("Raw Bytes");
    Bytes bytes = new Bytes("Raw Bytes", offsetHolder, size);
    bytes.addOffsetHolder( offsetHolder );
    bytes.addSizeHolder( size );
    addMember( bytes );
  }
}
