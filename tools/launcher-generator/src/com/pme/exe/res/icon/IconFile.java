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

package com.pme.exe.res.icon;

import com.pme.exe.Bin;
import com.pme.exe.res.Level;
import com.pme.exe.res.LevelEntry;

import java.io.*;

/**
 * Date: Apr 27, 2006
 * Time: 12:52:09 PM
 */
public class IconFile extends Bin.Structure {
  private Level myImages = new Level();
  private File myFile;

  public class IconWrongFormat extends IOException {
    public IconWrongFormat( File file ) {
      super("Icon file has wrong format:" + file.getPath());
    }
  }

  public IconFile(File file) {
    super(file.getName());
    myFile = file;
    addMember(new IconHeader());
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
    try {
      super.read(stream);
      Word idCount = (Word) ((Bin.Structure) getMember("Header")).getMember("idCount");
      ArrayOfBins<IconDirectory> iconDirs = new ArrayOfBins<IconDirectory>("Icon directories", IconDirectory.class, idCount);
      iconDirs.setCountHolder(idCount);
      addMember(myImages);
      Bin[] array = iconDirs.getArray();
      for (Bin bin : array) {
        myImages.addLevelEntry((LevelEntry) bin);
      }
      myImages.read(stream);
    }
    catch (IOException exception) {
      throw new IconWrongFormat( myFile );
    }
  }
}
