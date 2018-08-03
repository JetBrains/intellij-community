// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res;

import com.pme.exe.Bin;

import java.io.*;

/**
 * @author Sergey Zhulin
 * Date: Apr 20, 2006
 * Time: 3:56:31 PM
 */
public class Level extends Bin.Structure {
  private Level mySubLevel = null;

  public Level() {
    super("Level");
  }

  public void addLevelEntry(LevelEntry dir) {
    if (mySubLevel == null) {
      mySubLevel = new Level();
    }
    dir.setLevel(mySubLevel);
    addMember(dir);
  }

  public void insertLevelEntry(int index, LevelEntry dir) {
    if (mySubLevel == null) {
      mySubLevel = new Level();
    }
    dir.setLevel(mySubLevel);
    insertMember(index,dir);
  }

  public long sizeInBytes() {
    if (mySubLevel != null) {
      return super.sizeInBytes() + mySubLevel.sizeInBytes();
    }
    return super.sizeInBytes();
  }

  public void resetOffsets(long newOffset) {
    super.resetOffsets(newOffset);
    if (mySubLevel != null) {
      mySubLevel.resetOffsets(getOffset() + super.sizeInBytes());
    }
  }
                          /*
  public void copyFrom(Bin structure) {
    super.copyFrom(structure);
    if (mySubLevel != null) {
      mySubLevel.copyFrom(((Level)structure).mySubLevel);
    }
  }
                            */
  public void read(DataInput stream) throws IOException {
    super.read(stream);
    if (mySubLevel != null) {
      mySubLevel.read(stream);
    }
  }

  public void write(DataOutput stream) throws IOException {
    super.write(stream);
    if (mySubLevel != null) {
      mySubLevel.write(stream);
    }
  }

  public void report(OutputStreamWriter writer) throws IOException {
    super.report(writer);
    if (mySubLevel != null) {
      mySubLevel.report(writer);
    }
  }
}
