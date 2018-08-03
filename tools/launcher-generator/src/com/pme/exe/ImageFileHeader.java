// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe;

import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * @author Sergey Zhulin
 * Date: Mar 31, 2006
 * Time: 5:17:01 PM
 */
public class ImageFileHeader extends Bin.Structure {
  private final ImageFileHeader.Machine myMachine;

  public ImageFileHeader() {
    super("Image File Header");
    myMachine = new Machine();
    addMember(myMachine);
    addMember( new Word( "NumberOfSections" ) );
    addMember( new DWord( "TimeDateStamp" ) );
    addMember( new DWord( "PointerToSymbolTable" ) );
    addMember( new DWord( "NumberOfSymbols" ) );
    addMember( new Word( "SizeOfOptionalHeader" ) );
    addMember( new Word( "Characteristics" ) );
  }

  public long getMachine() {
    return myMachine.getValue();
  }

  class Machine extends Bin.Word{

    public Machine() {
      super("Machine");
    }

    public void report( OutputStreamWriter writer ) throws IOException {
      super.report( writer );
      long machine = getValue();
      if ( machine == 0x014c ){
        _report(writer ,"Machine: Intel 386" );
      } else if ( machine == 0x0002 ) {
        _report( writer, "Machine: Intel 64" );
      } else {
        _report( writer, "Machine: Unknown" );
      }
    }
  }
}
