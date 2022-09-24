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
import java.util.Arrays;
import java.util.Optional;

/**
 * @author Sergey Zhulin
 * Date: Mar 30, 2006
 * Time: 4:14:38 PM
 */
public class ExeReader extends Bin.Structure{
  private ArrayOfBins<ImageSectionHeader> mySectionHeaders;
  private SectionReader[] mySections;
  private final PeHeaderReader myPeHeader;
  private ImageOptionalHeader myImageOptionalHeader;
  private Bin.Bytes myBytes;
  private final Bin.Bytes myMsDosStub;
  private final MsDosHeader myMsDosHeader;

  public ExeReader(String name, ExeFormat exeFormat) {
    super(name);
    myMsDosHeader = new MsDosHeader();
    addMember( myMsDosHeader );
    //noinspection SpellCheckingInspection
    Bin.Value member = myMsDosHeader.getValueMember("lfanew");
    ValuesAdd size = new ValuesAdd( member, new DWord("").setValue( myMsDosHeader.sizeInBytes() ) );
    myMsDosStub = new Bytes( "MsDos stub program", size );
    addMember( myMsDosStub );
    myPeHeader = new PeHeaderReader(member, exeFormat);
    addMember( myPeHeader );
    if (exeFormat == ExeFormat.UNKNOWN) {
      return;
    }
    myImageOptionalHeader = (ImageOptionalHeader) myPeHeader.getMember("Image Optional Header");
    //noinspection unchecked
    mySectionHeaders = (ArrayOfBins<ImageSectionHeader>)myPeHeader.getMember( "ImageSectionHeaders" );
    addSizeHolder( myImageOptionalHeader.getValueMember( "SizeOfImage" ) );  //b164
  }

  public long sizeOfHeaders(){
    return myPeHeader.sizeInBytes() + myMsDosStub.sizeInBytes() + myMsDosHeader.sizeInBytes();
  }

  @Override
  public long sizeInBytes() {
    long result = 0;
    long va = 0;
    for ( int i = 0; i < mySectionHeaders.size(); ++i){
      ImageSectionHeader header = mySectionHeaders.get(i);
      Value virtualAddress = header.getValueMember("VirtualAddress");
      if ( va < virtualAddress.getValue() ){
        result = mySections[i].sizeInBytes() + virtualAddress.getValue();
      }
    }
    long div = result / 0x1000;
    long r = result % 0x1000;
    if ( r != 0 ){
      div++;
    }
    result = div * 0x1000;
    return result;
  }

  public SectionReader[] getSections(){
    return mySections;
  }
  public ArrayOfBins<ImageSectionHeader> getSectionHeaders(){
    return mySectionHeaders;
  }

  public SectionReader getSectionReader( String sectionName ){
    for (SectionReader section : mySections) {
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

    long filePointer = getOffset() + sizeOfHeaders();

    Bin.Value mainSectionsOffset;
    mySections = new SectionReader[mySectionHeaders.size()];
    for ( int i = 0; i < mySectionHeaders.size(); ++i ){
      ImageSectionHeader sectionHeader = mySectionHeaders.get(i);
      Bin.Value startOffset = sectionHeader.getValueMember( "PointerToRawData" );
      Bin.Value rva = sectionHeader.getValueMember( "VirtualAddress" );

      if ( i == 0 ){
        long size = startOffset.getValue() - filePointer;
        if ( myBytes == null ){
          myBytes = new Bytes( "Alignment", size );
          addMemberToMapOnly( myBytes );
        } else {
          myBytes = (Bytes)getMember( "Alignment" );
          myBytes.reset( (int)filePointer, (int)size );
        }
        myBytes.read( stream );
      }

      mainSectionsOffset = new ValuesAdd( rva, startOffset );
      mySections[i] = new SectionReader( sectionHeader, startOffset, mainSectionsOffset, myImageOptionalHeader );
      mySections[i].read( stream );
    }
    resetOffsets( 0 );
  }

  @Override
  public void resetOffsets(long newOffset) {
    super.resetOffsets(newOffset);
    long mainOffset = myPeHeader.getOffset() + myPeHeader.sizeInBytes() + myBytes.sizeInBytes();
    long offset = 0;
    for (SectionReader section : mySections) {
      section.resetOffsets(mainOffset + offset);
      offset += section.sizeInBytes();
    }
  }

  /**
   * Required for cases when sections change their size due to editing: the overlapping sections should be fixed
   * afterwards.
   */
  public void sectionVirtualAddressFixup() {
    long virtualAddress = mySectionHeaders.get(0).getValueMember("VirtualAddress").getValue();

    long sectionAlignment = myImageOptionalHeader.getValue("SectionAlignment");
    for (Bin sectionHeader : mySectionHeaders.getArray()) {
      Value virtualAddressMember = ((ImageSectionHeader)sectionHeader).getValueMember("VirtualAddress");

      // Section always starts from an address aligned to IMAGE_OPTIONAL_HEADER::SectionAlignment, which is 0x1000 by default
      if (virtualAddress % sectionAlignment != 0)
        virtualAddress += sectionAlignment - virtualAddress % sectionAlignment;

      virtualAddressMember.setValue(virtualAddress);
      virtualAddress += ((ImageSectionHeader)sectionHeader).getValueMember("VirtualSize").getValue();
    }

    // Update the relative virtual address of the Base Relocation Table, if any.
    Optional<ImageSectionHeader> relocationSectionHeader = Arrays.stream((ImageSectionHeader[])mySectionHeaders.getArray())
      .filter(sectionHeader -> ".reloc".equals(sectionHeader.getTxtMember("Name").getText())).findFirst();
    if (relocationSectionHeader.isPresent()) {
      Bin.ArrayOfBins imageDataDirectories = (Bin.ArrayOfBins)myImageOptionalHeader.getMember("ImageDataDirectories");
      ImageDataDirectory relocationDataDirectory = (ImageDataDirectory)imageDataDirectories.get(5);
      Value virtualAddressMember = relocationDataDirectory.getValueMember("VirtualAddress");
      virtualAddressMember.setValue(relocationSectionHeader.get().getValue("VirtualAddress"));
    }

    // The binary size has been changed as the result, update it in the size holders:
    updateSizeOffsetHolders();
  }

  @Override
  public void write(DataOutput stream) throws IOException {
    super.write(stream);
    myBytes.write(stream);
    for (SectionReader section : mySections) {
      section.write(stream);
    }
  }

  @Override
  public void report(OutputStreamWriter writer) throws IOException {
    super.report(writer);
    myBytes.report( writer );
    mySectionHeaders.report(writer);
    for (SectionReader section : mySections) {
      section.report(writer);
    }
  }

  public ExeFormat getExeFormat() {
    long machine = myPeHeader.getImageFileHeader().getMachine();
    if (machine == 0x14c) {
      return ExeFormat.X86;
    }
    if (machine == 0x8664) {
      return ExeFormat.X64;
    }
    if (machine == 0xAA64) {
      return ExeFormat.ARM64;
    }
    throw new UnsupportedOperationException("Unsupported machine code " + machine);
  }
}
