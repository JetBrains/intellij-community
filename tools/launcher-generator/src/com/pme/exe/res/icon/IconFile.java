// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res.icon;

import com.pme.exe.Bin;
import com.pme.exe.res.Level;
import com.pme.exe.res.LevelEntry;

import java.io.*;

/**
 * @author Sergey Zhulin
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
