// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe;

import java.io.IOException;
import java.io.DataInput;

/**
 * @author Sergey Zhulin
 * Date: Mar 31, 2006
 * Time: 2:10:01 PM
 */
public class MsDosHeader extends Bin.Structure {
  public MsDosHeader() {
    super("MSDOS Header");
    addMember( new Word( "magic" ), "Magic number" );
    addMember( new Word( "cblp" ), "Bytes on last page of file" );
    addMember( new Word( "cp" ), "Pages in file" );
    addMember( new Word( "crlc" ), "Relocations" );
    addMember( new Word( "cparhdr" ), "Size of header in paragraphs" );
    addMember( new Word( "minalloc" ), "Minimum extra paragraphs needed" );
    addMember( new Word( "maxalloc" ), "Maximum extra paragraphs needed" );
    addMember( new Word( "ss" ), "Initial (relative) SS value" );
    addMember( new Word( "sp" ), "Initial SP value" );
    addMember( new Word( "csum" ), "Checksum" );
    addMember( new Word( "ip" ), "Initial IP value" );
    addMember( new Word( "cs" ), "Initial (relative) CS value" );
    addMember( new Word( "lfarlc" ), "File address of relocation table" );
    addMember( new Word( "ovno" ), "Overlay number" );
    addMember( new ArrayOfBins( "res", Word.class,4 ), "Reserved words" );
    addMember( new Word( "oemid" ), "OEM identifier (for e_oeminfo)" );
    addMember( new Word( "oeminfo" ), "OEM information; e_oemid specific" );
    addMember( new ArrayOfBins( "res2", Word.class, 10 ), "Reserved words" );
    addMember( new DWord( "lfanew" ), "File address of new exe header" );
  }

  public void read(DataInput stream) throws IOException {
    super.read( stream );
    long magic = getValue( "magic" );
    if (magic != 0x5a4d) {
      throw new InvalidMsDosHeaderException("First two chars in exe file must be 'MZ'");
    }
  }
}
