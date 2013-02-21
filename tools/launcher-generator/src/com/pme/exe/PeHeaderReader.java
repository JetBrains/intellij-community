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
import java.io.DataInput;

/**
 * Date: Mar 31, 2006
 * Time: 5:00:49 PM
 */
public class PeHeaderReader extends Bin.Structure{
  private Bin.Value myStartOffset;

  public PeHeaderReader( Bin.Value startOffset ){
    super( "PE Header" );
    myStartOffset = startOffset;
    addOffsetHolder( myStartOffset );
    addMember( new DWord( "Signature" ) );
    ImageFileHeader imageFileHeader = new ImageFileHeader();
    addMember( imageFileHeader );
    addMember( new ImageOptionalHeader() );
    Bin.Value numberOfSections = imageFileHeader.getValueMember( "NumberOfSections" );
    ArrayOfBins imageSectionHeaders = new ArrayOfBins( "ImageSectionHeaders", ImageSectionHeader.class, numberOfSections );
    imageSectionHeaders.setCountHolder( numberOfSections );
    addMember( imageSectionHeaders );
  }
  public void read(DataInput stream) throws IOException {
    super.read( stream );
  }
}
