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

import com.pme.exe.res.ValuesAdd;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

/**
 * @author Sergey Zhulin
 * Date: Mar 30, 2006
 * Time: 4:14:38 PM
 */
public class ExeReader extends Bin.Structure {
  private final ArrayOfBins<ImageSectionHeader> mySectionHeaders;
  private final ArrayList<Section> mySections = new ArrayList<>();
  private final PeHeaderReader myPeHeader;
  private final ImageOptionalHeader myImageOptionalHeader;
  private final Bin.Bytes myPadding;
  private final Bin.Bytes myMsDosStub;
  private final MsDosHeader myMsDosHeader;

  public ExeReader(String name) {
    super(name);
    myMsDosHeader = addMember(new MsDosHeader());
    ValuesAdd msDosStubSize = new ValuesAdd(myMsDosHeader.getPEHeaderOffset(), new DWord("").setValue(myMsDosHeader.sizeInBytes()));
    myMsDosStub = addMember(new Bytes("MsDos stub program", msDosStubSize));
    myPeHeader = addMember(new PeHeaderReader(myMsDosHeader.getPEHeaderOffset()));
    myPadding = new Bytes("Padding between headers and first segment",
                          new ValuesAdd(myPeHeader.getImageOptionalHeader().getSizeOfHeaders(), new ReadOnlyValue("") {
                            @Override
                            public long getValue() {
                              return myPeHeader.sizeInBytes() + myMsDosStub.sizeInBytes() + myMsDosHeader.sizeInBytes();
                            }
                          }));
    addMember(myPadding);
    myImageOptionalHeader = myPeHeader.getImageOptionalHeader();
    mySectionHeaders = myPeHeader.getImageSectionHeaders();
  }

  @Override
  public long sizeInBytes() {
    long result = myPeHeader.sizeInBytes() + myMsDosStub.sizeInBytes() + myMsDosHeader.sizeInBytes() + myPadding.sizeInBytes();
    for (Section section : mySections) {
      result += section.sizeInBytes();
    }
    return result;
  }

  public long virtualSizeInBytes() {
    // AKA VirtualAddress + VirtualSize of the latest section, must be dividable by SectionAlignment

    long sectionAlignment = myImageOptionalHeader.getSectionAlignment().getValue();
    long result = 0;
    long lastVA = 0;
    for (ImageSectionHeader header : mySectionHeaders) {
      long va = header.getVirtualAddress().getValue();
      if (lastVA < va) {
        result = header.getVirtualSize().getValue() + va;
      }
    }
    if (result % sectionAlignment != 0) {
      result += sectionAlignment - result % sectionAlignment;
    }
    return result;
  }

  public ArrayOfBins<ImageSectionHeader> getSectionHeaders() {
    return mySectionHeaders;
  }

  public PeHeaderReader getPeHeader() {
    return myPeHeader;
  }

  public Section getSectionReader(String sectionName) {
    for (Section section : mySections) {
      if (sectionName.equals(section.getSectionName())) {
        return section;
      }
    }
    return null;
  }

  @Override
  public void read(DataInput stream) throws IOException {
    super.read(stream);
    if (mySectionHeaders == null) {
      return;
    }

    DWord sizeOfHeaders = myPeHeader.getImageOptionalHeader().getSizeOfHeaders();
    assert sizeOfHeaders.getValue() == mySectionHeaders.get(0).getPointerToRawData().getValue();

    mySections.clear();
    for (ImageSectionHeader sectionHeader : mySectionHeaders) {
      Section section = Section.newSection(sectionHeader, myImageOptionalHeader);
      mySections.add(section);
      section.read(stream);
    }
    resetOffsets(0);
  }

  @Override
  public void resetOffsets(long newOffset) {
    super.resetOffsets(newOffset);
    long mainOffset = myPeHeader.getOffset() + myPeHeader.sizeInBytes() + myPadding.sizeInBytes();
    long mainOffset2 = myPeHeader.sizeInBytes() + myMsDosStub.sizeInBytes() + myMsDosHeader.sizeInBytes() + myPadding.sizeInBytes();
    assert mainOffset == myPeHeader.getImageOptionalHeader().getSizeOfHeaders().getValue();
    assert mainOffset == mainOffset2;
    long offset = 0;
    for (Section section : mySections) {
      section.resetOffsets(mainOffset + offset);
      offset += section.sizeInBytes();
    }

    // Update SizeOfImage with not real bytes size, but with virtual size
    myImageOptionalHeader.getSizeOfImage().setValue(virtualSizeInBytes());
  }

  /**
   * Required for cases when sections change their size due to editing: the overlapping sections should be fixed
   * afterwards.
   */
  public void sectionVirtualAddressFixup() {
    long virtualAddress = mySectionHeaders.get(0).getVirtualAddress().getValue();

    long sectionAlignment = myImageOptionalHeader.getSectionAlignment().getValue();
    for (ImageSectionHeader header : mySectionHeaders) {

      // Section always starts from an address aligned to IMAGE_OPTIONAL_HEADER::SectionAlignment, which is 0x1000 by default
      if (virtualAddress % sectionAlignment != 0) {
        virtualAddress += sectionAlignment - virtualAddress % sectionAlignment;
      }

      header.getVirtualAddress().setValue(virtualAddress);
      virtualAddress += header.getVirtualSize().getValue();
    }

    // Update the relative virtual address of the Base Relocation Table, if any.
    updateDataDirectoryFromSectionHeader(Section.RESOURCES_SECTION_NAME, ImageDataDirectory.IMAGE_DIRECTORY_ENTRY_RESOURCE);
    updateDataDirectoryFromSectionHeader(Section.RELOCATIONS_SECTION_NAME, ImageDataDirectory.IMAGE_DIRECTORY_ENTRY_BASERELOC);

    // Resources section may have changed virtual offset, re-shake of all nested structures required to have correct addresses,
    // also the binary size may have been changed as the result
    resetOffsets(0);
  }

  private void updateDataDirectoryFromSectionHeader(String name, int index) {
    ImageSectionHeader header = getSection(name);
    ImageDataDirectory directory = myImageOptionalHeader.getImageDataDirectories().get(index);
    directory.getVirtualAddress().setValue(header.getVirtualAddress().getValue());
    directory.getSize().setValue(header.getVirtualSize().getValue());
  }

  @Override
  public void write(DataOutput stream) throws IOException {
    super.write(stream);
    for (Section section : mySections) {
      section.write(stream);
    }
  }

  @Override
  public void report(OutputStreamWriter writer) throws IOException {
    super.report(writer);
    myPadding.report(writer);
    mySectionHeaders.report(writer);
    for (Section section : mySections) {
      section.report(writer);
    }
  }

  private ImageSectionHeader getSection(String name) {
    for (ImageSectionHeader header : mySectionHeaders) {
      if (name.equals(header.getSectionName())) {
        return header;
      }
    }
    throw new IllegalStateException("Cannot find section with name " + name);
  }
}
