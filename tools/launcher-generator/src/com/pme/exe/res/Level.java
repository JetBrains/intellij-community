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

package com.pme.exe.res;

import com.pme.exe.Bin;

import java.io.*;

/**
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
