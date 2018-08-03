// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe;

/**
 * @author Sergey Zhulin
 * Date: Mar 31, 2006
 * Time: 5:00:49 PM
 */
public class PeHeaderReader extends Bin.Structure{
  private final ImageFileHeader myImageFileHeader;

  public PeHeaderReader(Bin.Value startOffset, ExeFormat exeFormat) {
    super( "PE Header" );
    addOffsetHolder(startOffset);
    addMember( new DWord( "Signature" ) );
    myImageFileHeader = new ImageFileHeader();
    addMember(myImageFileHeader);
    if (exeFormat == ExeFormat.UNKNOWN) {
      return;
    }
    addMember(new ImageOptionalHeader(exeFormat));
    Bin.Value numberOfSections = myImageFileHeader.getValueMember("NumberOfSections");
    ArrayOfBins imageSectionHeaders = new ArrayOfBins( "ImageSectionHeaders", ImageSectionHeader.class, numberOfSections );
    imageSectionHeaders.setCountHolder( numberOfSections );
    addMember( imageSectionHeaders );
  }

  public ImageFileHeader getImageFileHeader() {
    return myImageFileHeader;
  }
}
