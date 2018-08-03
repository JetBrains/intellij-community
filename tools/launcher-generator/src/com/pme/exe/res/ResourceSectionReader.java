// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res;

import com.pme.exe.Bin;
import com.pme.exe.ImageSectionHeader;
import com.pme.exe.ImageOptionalHeader;
import com.pme.exe.ImageDataDirectory;

import java.io.*;

/**
 * @author Sergey Zhulin
 * Date: Apr 6, 2006
 * Time: 7:10:09 PM
 */
public class ResourceSectionReader extends Bin.Structure {
  private Level myRoot = new Level();
  private Bin.Value myStartOffset;
  private Bin.Value myMainSectionsOffset;
  private Bin.Value mySize;
  private Bin.Bytes myBytes;

  public ResourceSectionReader( ImageSectionHeader sectionHeader, Bin.Value startOffset, Bin.Value mainSectionsOffset, ImageOptionalHeader imageOptionalHeader) {
    super(((Bin.Txt)sectionHeader.getMember("Name")).getText());
    myStartOffset = startOffset;
    mySize = sectionHeader.getValueMember( "SizeOfRawData" );
    Bin.Value virtualSize = sectionHeader.getValueMember( "VirtualSize" );
    myMainSectionsOffset = mainSectionsOffset;
    addOffsetHolder(startOffset);

    ArrayOfBins imageDataDirs = (ArrayOfBins)imageOptionalHeader.getMember( "ImageDataDirectories" );
    Bin[] bins = imageDataDirs.getArray();
    ImageDataDirectory imageDataDirectory = (ImageDataDirectory)bins[2];
    Value size = imageDataDirectory.getValueMember("Size");

    addSizeHolder(mySize);
    addSizeHolder(virtualSize);
    addSizeHolder(size);
    myRoot.addLevelEntry(new DirectoryEntry( this, null, 0));
    addMember(myRoot);
  }

  public DirectoryEntry getRoot(){
    return (DirectoryEntry)myRoot.getMember( "IRD" );
  }

  public Value getStartOffset() {
    return myStartOffset;
  }

  public Value getMainSectionsOffset() {
    return myMainSectionsOffset;
  }

  public long sizeInBytes() {
    return super.sizeInBytes() + myBytes.sizeInBytes();
  }

  public void read(DataInput stream) throws IOException {
    super.read(stream);
    DWord size = new DWord("size");
    size.setValue((mySize.getValue() - myRoot.sizeInBytes()));
    DWord startOffset = new DWord("startOffset");
    startOffset.setValue((myStartOffset.getValue() + myRoot.sizeInBytes()));
    myBytes = new Bin.Bytes("Raw data", startOffset, size);
    myBytes.read(stream);
  }

  public void write(DataOutput stream) throws IOException {
    super.write(stream);
    myBytes.write(stream);
  }

  public void report(OutputStreamWriter writer) throws IOException {
    super.report(writer);
    myBytes.report(writer);
  }
}
