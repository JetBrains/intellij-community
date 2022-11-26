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

package com.pme.exe.res.icon;

import com.pme.exe.Bin;

import java.io.*;

/**
 * @author Sergey Zhulin
 * Date: Apr 27, 2006
 * Time: 12:52:09 PM
 */
public class IconFile extends Bin.Structure {
  private final IconHeader myHeader;
  private final ArrayOfBins<IconDirectory> myIcons;

  public static class IconWrongFormat extends IOException {
    public IconWrongFormat(File file) {
      super("Icon file has wrong format:" + file.getPath());
    }
  }

  public IconFile() {
    super("IconFile");
    myHeader = addMember(new IconHeader());
    Word idCount = myHeader.getCount();
    myIcons = addMember(new ArrayOfBins<>("Icon directories", IconDirectory.class, idCount));
    myIcons.setCountHolder(idCount);
  }

  public IconHeader getHeader() {
    return myHeader;
  }

  public ArrayOfBins<IconDirectory> getIcons() {
    return myIcons;
  }

  public void read(File file) throws IOException {
    setName(file.getName());
    try (RandomAccessFile stream = new RandomAccessFile(file, "r")) {
      read(stream);
    }
    catch (IOException exception) {
      throw new IconWrongFormat(file);
    }
  }

  @Override
  public void read(DataInput stream) throws IOException {
    super.read(stream);
    for (IconDirectory icon : myIcons) {
      icon.getBytes().read(stream);
    }
  }

  @Override
  public void write(DataOutput stream) throws IOException {
    super.write(stream);
    for (IconDirectory icon : myIcons) {
      icon.getBytes().write(stream);
    }
  }
}
