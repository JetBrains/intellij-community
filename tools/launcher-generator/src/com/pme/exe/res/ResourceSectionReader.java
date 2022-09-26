/*
 * Copyright 2006 ProductiveMe Inc.
 * Copyright 2013-2018 JetBrains s.r.o.
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
  private long myFileAlignment;

  public ResourceSectionReader( ImageSectionHeader sectionHeader, Bin.Value startOffset, Bin.Value mainSectionsOffset, ImageOptionalHeader imageOptionalHeader) {
    super(((Bin.Txt)sectionHeader.getMember("Name")).getText());
    myStartOffset = startOffset;
    mySize = sectionHeader.getValueMember( "SizeOfRawData" );
    Bin.Value virtualSize = sectionHeader.getValueMember( "VirtualSize" );
    myMainSectionsOffset = mainSectionsOffset;
    addOffsetHolder(startOffset);
    myFileAlignment = imageOptionalHeader.getValue("FileAlignment");

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
    long size = super.sizeInBytes() + myBytes.sizeInBytes();
    if (size % myFileAlignment != 0)
      size = (size / myFileAlignment + 1) * myFileAlignment;
    return size;
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

    long paddingSize = sizeInBytes() - super.sizeInBytes() - myBytes.sizeInBytes();
    if (paddingSize != 0)
      stream.write(new byte[(int)paddingSize]);
  }

  public void report(OutputStreamWriter writer) throws IOException {
    super.report(writer);
    myBytes.report(writer);
  }
}
