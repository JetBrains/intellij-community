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

package com.pme.exe.res;

import com.pme.exe.Bin;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

/**
 * @author Sergey Zhulin
 * Date: Apr 20, 2006
 * Time: 3:56:31 PM
 */
public class Level extends Bin.Structure {
  private Level mySubLevel = null;
  private final int myDepth;

  public Level(int depth) {
    super("Level" + depth);
    myDepth = depth;
  }

  public void addLevelEntry(LevelEntry dir) {
    if (mySubLevel == null) {
      mySubLevel = new Level(myDepth + 1);
    }
    dir.setLevel(mySubLevel);
    addMember(dir);
  }

  @Override
  public long sizeInBytes() {
    long size = super.sizeInBytes();
    if (mySubLevel != null) {
      size += mySubLevel.sizeInBytes();
    }
    return size;
  }

  @Override
  public void resetOffsets(long newOffset) {
    super.resetOffsets(newOffset);
    if (mySubLevel != null) {
      mySubLevel.resetOffsets(getOffset() + super.sizeInBytes());
    }
  }

  @Override
  public void read(DataInput stream) throws IOException {
    sortMembersIfNeeded();
    super.read(stream);
    if (mySubLevel != null) {
      mySubLevel.read(stream);
    }
  }

  @Override
  public void write(DataOutput stream) throws IOException {
    super.write(stream);
    if (mySubLevel != null) {
      mySubLevel.write(stream);
    }
  }

  @SuppressWarnings("SSBasedInspection")
  private void sortMembersIfNeeded() {
    ArrayList<Bin> members = getMembers();
    if (members.stream().allMatch(bin -> bin instanceof RawResource)) {
      // Somehow RawResources could go out of order on disk, let's sort them, so we won't need to seek them in input stream
      members.sort((o1, o2) -> {
        assert o1 instanceof RawResource;
        assert o2 instanceof RawResource;
        return Long.compare(((RawResource)o1).getRawOffset().getValue(), ((RawResource)o2).getRawOffset().getValue());
      });
    }
  }

  @Override
  public void report(OutputStreamWriter writer) throws IOException {
    super.report(writer);
    if (mySubLevel != null) {
      mySubLevel.report(writer);
    }
  }

  @Override
  public String toString() {
    return "Level{" +
           "depth=" + myDepth +
           ", members_num=" + getMembers().size() +
           ", sub=" + mySubLevel +
           '}';
  }
}
