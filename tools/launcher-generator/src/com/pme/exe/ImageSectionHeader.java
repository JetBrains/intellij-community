// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe;

/**
 * @author Sergey Zhulin
 * Date: Apr 1, 2006
 * Time: 1:54:57 PM
 */
public class ImageSectionHeader extends Bin.Structure{
  public ImageSectionHeader() {
    super("Image section header");
    addMember( new Txt( "Name", 8 ));
    addMember( new DWord( "VirtualSize"));
    addMember( new DWord( "VirtualAddress"));
    addMember( new DWord( "SizeOfRawData"));
    addMember( new DWord( "PointerToRawData"));
    addMember( new DWord( "PointerToRelocations"));
    addMember( new DWord( "PointerToLinenumbers"));
    addMember( new Word( "NumberOfRelocations"));
    addMember( new Word( "NumberOfLinenumbers"));
    addMember( new DWord( "Characteristics"));
  }
}
