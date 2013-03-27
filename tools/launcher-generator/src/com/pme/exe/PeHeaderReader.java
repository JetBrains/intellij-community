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

/**
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
