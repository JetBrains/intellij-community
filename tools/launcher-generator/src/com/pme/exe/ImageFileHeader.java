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

import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * @author Sergey Zhulin
 * Date: Mar 31, 2006
 * Time: 5:17:01 PM
 */
public class ImageFileHeader extends Bin.Structure {
  private final ImageFileHeader.Machine myMachine;
  private final Word myNumberOfSections;

  public ImageFileHeader() {
    super("Image File Header");
    myMachine = addMember(new Machine());
    myNumberOfSections = addMember(new Word("NumberOfSections"));
    addMember( new DWord( "TimeDateStamp" ) );
    addMember( new DWord( "PointerToSymbolTable" ) );
    addMember( new DWord( "NumberOfSymbols" ) );
    addMember( new Word( "SizeOfOptionalHeader" ) );
    addMember( new Word( "Characteristics" ) );
  }

  public long getMachine() {
    return myMachine.getValue();
  }

  public Word getNumberOfSections() {
    return myNumberOfSections;
  }

  static class Machine extends Bin.Word{

    Machine() {
      super("Machine");
    }

    @Override
    public void report(OutputStreamWriter writer ) throws IOException {
      super.report( writer );
      long machine = getValue();
      if ( machine == 0x014c ){
        _report(writer ,"Machine: Intel 386" );
      } else if ( machine == 0x0002 ) {
        _report( writer, "Machine: Intel 64" );
      } else if ( machine == 0x8664 ) {
        _report( writer, "Machine: AMD64" );
      } else if ( machine == 0xAA64 ) {
        _report( writer, "Machine: ARM64" );
      } else {
        _report( writer, String.format("Machine: Unknown (%#06x)", machine));
      }
    }
  }
}
