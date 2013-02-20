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

package com.pme.exe.res.bmp;

import com.pme.exe.Bin;

import java.io.File;
import java.io.DataInput;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Date: May 3, 2006
 * Time: 12:33:01 PM
 */
public class BmpFile extends Bin.Structure {
  private File myFile;
  private BitmapFileHeader myHeader = new BitmapFileHeader();

  public BmpFile(File file) {
    super(file.getName());
    myFile = file;
    addMember( myHeader );
  }

  public void read() throws IOException {
    RandomAccessFile stream = null;
    try {
      stream = new RandomAccessFile(myFile, "r");
      read(stream);
    } finally {
      if (stream != null) {
        stream.close();
      }
    }
  }

  public void read(DataInput stream) throws IOException {
    super.read(stream);
    Value bfSize = myHeader.getValueMember("bfSize");
    long size = bfSize.getValue() - sizeInBytes();
    Bytes bytes = new Bytes( "Data", size );
    bytes.read( stream );
    addMember( bytes );
  }
}
