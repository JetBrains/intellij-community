// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res.vi;

import com.pme.exe.Bin;
import com.pme.util.StreamUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;


public abstract class VersionInfoBin extends Bin.Structure {
  private String myExpectedName;
  private VersionInfoFactory myChildFactory;
  protected final Word myLength;
  protected final WCharStringNT myKey;
  protected final Word myValueLength;

  public VersionInfoBin(String name) {
    super(name);
    myLength = addMember(new Word("wLength"));
    addSizeHolder(myLength);
    myValueLength = addMember(new Word("wValueLength"));
    addMember(new Word("wType"));
    myKey = addMember(new WCharStringNT("szKey"));
    addMember(new Padding("Padding", 4));
  }

  public VersionInfoBin(String versionInfo, String expectedName) {
    this(versionInfo);
    myExpectedName = expectedName;
  }

  public VersionInfoBin(String versionInfo, String expectedName, VersionInfoFactory childFactory) {
    this(versionInfo, expectedName);
    myChildFactory = childFactory;
  }

  @Override
  public void read(DataInput stream) throws IOException {
    long startOffset = StreamUtil.getOffset(stream);
    assert startOffset % 4 == 0;
    super.read(stream);
    if (myExpectedName != null) {
      String signature = myKey.getValue();
      if (!signature.equals(myExpectedName)) {
        throw new IllegalStateException("Expected signature '" + myExpectedName + "', found '" + signature + "'");
      }
    }
    if (myChildFactory != null) {
      long length = myLength.getValue();
      int i = 0;
      while (StreamUtil.getOffset(stream) < startOffset + length) {
        VersionInfoBin child = myChildFactory.createChild(i++);
        child.read(stream);
        addMember(child);
      }
    }
  }

  @Override
  public void write(DataOutput stream) throws IOException {
    long startOffset = StreamUtil.getOffset(stream);
    super.write(stream);
    long offset = StreamUtil.getOffset(stream);
    long realLength = offset - startOffset;
    long expectedLength = myLength.getValue();
    assert realLength == expectedLength : "Actual length does not match calculated length for " + getName() +
                                          ": expected " + expectedLength + ", actual " + realLength + ", sizeInBytes() " + sizeInBytes();
  }
}
