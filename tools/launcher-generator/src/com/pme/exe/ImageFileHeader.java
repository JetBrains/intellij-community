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

package com.pme.exe;

import java.io.IOException;
import java.io.OutputStreamWriter;

/**
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
