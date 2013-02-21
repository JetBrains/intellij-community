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

import com.pme.exe.res.ValuesAdd;

import java.io.*;

/**
 * Date: Mar 30, 2006
 * Time: 4:14:38 PM
 */
public class ExeReader extends Bin.Structure{
  private ArrayOfBins mySectionHeaders;
  private SectionReader[] mySections;
  private PeHeaderReader myPeHeader;
  private ImageOptionalHeader myImageOptionalHeader;
  private Bin.Bytes myBytes;
  private Bin.Bytes myMsDosStub;
  private MsDosHeader myMsDosHeader;
  public ExeReader(String name) {
    super(name);
    myMsDosHeader = new MsDosHeader();
    addMember( myMsDosHeader );
    Bin.Value member = myMsDosHeader.getValueMember("lfanew");
    ValuesAdd size = new ValuesAdd( member, new DWord("").setValue( myMsDosHeader.sizeInBytes() ) );
    myMsDosStub = new Bytes( "MsDos stub program", size );
    addMember( myMsDosStub );
    myPeHeader = new PeHeaderReader( member );
    addMember( myPeHeader );
    myImageOptionalHeader = (ImageOptionalHeader) myPeHeader.getMember("Image Optional Header");
    mySectionHeaders = (ArrayOfBins)myPeHeader.getMember( "ImageSectionHeaders" );
    addSizeHolder( myImageOptionalHeader.getValueMember( "SizeOfImage" ) );  //b164
  }

  public long sizeOfHeaders(){
    return myPeHeader.sizeInBytes() + myMsDosStub.sizeInBytes() + myMsDosHeader.sizeInBytes();
  }

  public long sizeInBytes() {
    long result = 0;
    long va = 0;
    for ( int i = 0; i < mySectionHeaders.size(); ++i){
      ImageSectionHeader header = (ImageSectionHeader)mySectionHeaders.get(i);
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
  public ArrayOfBins getSectionHeaders(){
    return mySectionHeaders;
  }

  public SectionReader getSectionReader( String sectionName ){
    for (int i = 0; i < mySections.length; i++) {
      SectionReader section = mySections[i];
      if ( sectionName.equals( section.getSectionName() )){
        return section;
      }
    }
    return null;
  }

  public void read(DataInput stream) throws IOException {
    super.read(stream);

    long filePointer = getOffset() + sizeOfHeaders();

    Bin.Value mainSectionsOffset;
    mySections = new SectionReader[mySectionHeaders.size()];
    for ( int i = 0; i < mySectionHeaders.size(); ++i ){
      ImageSectionHeader sectionHeader = (ImageSectionHeader)mySectionHeaders.get(i);
      Bin.Value startOffset = sectionHeader.getValueMember( "PointerToRawData" );
      Bin.Value rva = sectionHeader.getValueMember( "VirtualAddress" );

      if ( i == 0 ){
        long size = startOffset.getValue() - filePointer;
        if ( myBytes == null ){
          myBytes = new Bytes( "Aligment", size );
          addMemberToMapOnly( myBytes );
        } else {
          myBytes = (Bytes)getMember( "Aligment" );
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

  public void resetOffsets(long newOffset) {
    super.resetOffsets(newOffset);
    long mainOffset = myPeHeader.getOffset() + myPeHeader.sizeInBytes() + myBytes.sizeInBytes();
    long offset = 0;
    for (int i = 0; i < mySections.length; i++) {
      Bin section = mySections[i];
      section.resetOffsets( mainOffset + offset );
      offset += section.sizeInBytes();
    }
  }

  public void write(DataOutput stream) throws IOException {
    super.write(stream);
    myBytes.write( stream );
    for (int i = 0; i < mySections.length; i++) {
      mySections[i].write( stream );
    }
  }

  public void report(OutputStreamWriter writer) throws IOException {
    super.report(writer);
    myBytes.report( writer );
    mySectionHeaders.report( writer );
    for ( int i = 0; i < mySections.length; ++i ){
      mySections[i].report( writer );
    }
  }

  public static void main(String[] args) throws Throwable {
    ExeReader reader = new ExeReader( "SeaShell.exe" );
    File file = new File( "C:\\work\\SeaShell.exe" );
    RandomAccessFile stream = new RandomAccessFile( file, "r" );
    reader.read( stream );
    stream.close();
  }
}
