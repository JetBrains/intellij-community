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

/**
 * @author Sergey Zhulin
 * Date: Mar 31, 2006
 * Time: 5:00:49 PM
 */
public class PeHeaderReader extends Bin.Structure {
  private final ImageFileHeader myImageFileHeader;
  private final ImageOptionalHeader myImageOptionalHeader;
  private final ArrayOfBins<ImageSectionHeader> myImageSectionHeaders;

  public PeHeaderReader(Bin.Value startOffset) {
    super("PE Header");
    addOffsetHolder(startOffset);
    addMember(new DWord("Signature"));
    myImageFileHeader = addMember(new ImageFileHeader());
    myImageOptionalHeader = addMember(new ImageOptionalHeader());
    Bin.Word numberOfSections = myImageFileHeader.getNumberOfSections();
    myImageSectionHeaders = addMember(new ArrayOfBins<>("ImageSectionHeaders", ImageSectionHeader.class, numberOfSections));
    myImageSectionHeaders.setCountHolder(numberOfSections);
  }

  public ImageFileHeader getImageFileHeader() {
    return myImageFileHeader;
  }

  public ImageOptionalHeader getImageOptionalHeader() {
    return myImageOptionalHeader;
  }

  public ArrayOfBins<ImageSectionHeader> getImageSectionHeaders() {
    return myImageSectionHeaders;
  }
}
