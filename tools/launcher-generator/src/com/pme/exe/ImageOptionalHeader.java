// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe;

/**
 * @author Sergey Zhulin
 * Date: Mar 31, 2006
 * Time: 6:01:16 PM
 */
public class ImageOptionalHeader extends Bin.Structure {
  public ImageOptionalHeader(ExeFormat format) {
    super("Image Optional Header");
    addMember( new Word( "Magic" ) );
    addMember( new Byte( "MajorLinkerVersion"));
    addMember( new Byte( "MinorLinkerVersion"));
    addMember( new DWord( "SizeOfCode" ) );
    addMember( new DWord( "SizeOfInitializedData" ) );
    addMember( new DWord( "SizeOfUninitializedData" ) );
    addMember( new DWord( "AddressOfEntryPoint" ) );
    addMember( new DWord( "BaseOfCode" ) );
    if (format == ExeFormat.X86) {
      addMember( new DWord( "BaseOfData" ) );
      addMember( new DWord( "ImageBase" ) );
    }
    else {
      addMember(new LongLong("ImageBase"));
    }
    addMember( new DWord( "SectionAlignment" ) );
    addMember( new DWord( "FileAlignment" ) );
    addMember( new Word( "MajorOperatingSystemVersion" ) );
    addMember( new Word( "MinorOperatingSystemVersion" ) );
    addMember( new Word( "MajorImageVersion" ) );
    addMember( new Word( "MinorImageVersion" ) );
    addMember( new Word( "MajorSubsystemVersion" ) );
    addMember( new Word( "MinorSubsystemVersion" ) );
    addMember( new DWord( "Win32VersionValue" ) );
    addMember( new DWord( "SizeOfImage" ) );
    addMember( new DWord( "SizeOfHeaders" ) );
    addMember( new DWord( "CheckSum" ) );
    addMember( new Word( "Subsystem" ) );
    addMember( new Word( "DllCharacteristics" ) );
    if (format == ExeFormat.X86) {
      addMember( new DWord( "SizeOfStackReserve" ) );
      addMember( new DWord( "SizeOfStackCommit" ) );
      addMember( new DWord( "SizeOfHeapReserve" ) );
      addMember( new DWord( "SizeOfHeapCommit" ) );
    }
    else {
      addMember( new LongLong( "SizeOfStackReserve" ) );
      addMember( new LongLong( "SizeOfStackCommit" ) );
      addMember( new LongLong( "SizeOfHeapReserve" ) );
      addMember( new LongLong( "SizeOfHeapCommit" ) );
    }
    addMember( new DWord( "LoaderFlags" ) );
    DWord numberOfRvaAndSizes = (DWord)addMember( new DWord( "NumberOfRvaAndSizes") );
    ArrayOfBins<ImageDataDirectory> imageDataDirectories = new ArrayOfBins<ImageDataDirectory>("ImageDataDirectories", ImageDataDirectory.class, numberOfRvaAndSizes);
    imageDataDirectories.setCountHolder( numberOfRvaAndSizes );

    addMember( imageDataDirectories );
  }
}
