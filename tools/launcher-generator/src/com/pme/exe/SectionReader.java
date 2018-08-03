// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe;

import com.pme.exe.res.ResourceSectionReader;

import java.io.OutputStreamWriter;
import java.io.IOException;

/**
 * @author Sergey Zhulin
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
