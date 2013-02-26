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

import com.pme.exe.res.ResourceSectionReader;

import java.io.OutputStreamWriter;
import java.io.IOException;

/**
 * Date: Apr 5, 2006
 * Time: 9:48:35 AM
 */
public class SectionReader extends Bin.Structure{
  private Bin.Txt myName;
  public SectionReader( ImageSectionHeader sectionHeader, Bin.Value startOffset, Bin.Value mainSectionsOffset, ImageOptionalHeader imageOptionalHeader ) {
    super("Section");

    myName = (Bin.Txt)sectionHeader.getMember("Name");
    String sectionName = myName.getText();
    if ( ".rsrc".equals(sectionName) ){
      addMember( new ResourceSectionReader( sectionHeader, startOffset, mainSectionsOffset, imageOptionalHeader ) );
    } else {
      Bin.Value size = sectionHeader.getValueMember( "SizeOfRawData" );
      addMember( new Simple( sectionName, startOffset, size ) );
    }
  }

  public String getSectionName(){
    return myName.getText();
  }

  public void report(OutputStreamWriter writer) throws IOException {
    _report( writer, "Section name: " + myName.getText() );
    super.report(writer);
  }

  public static class Simple extends Bin.Structure{

    public Simple(String name, Bin.Value startOffset, Bin.Value size) {
      super(name);
      addOffsetHolder( startOffset );
      addSizeHolder( size );
      addMember( new Bin.Bytes( "Raw data", size ) );
    }
  }

}
