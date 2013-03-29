package com.pme.exe.res.vi;

import com.pme.exe.Bin;
import com.pme.util.OffsetTrackingInputStream;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author yole
 */
public class VersionInfoBin extends Bin.Structure {
  private String myExpectedName;
  private VersionInfoFactory myChildFactory;

  public VersionInfoBin(String name) {
    super(name);
    Word length = new Word("wLength");
    addMember(length);
    addSizeHolder(length);
    addMember(new Word("wValueLength"));
    addMember(new Word("wType"));
    addMember(new WChar("szKey"));
    addMember(new Padding(4));
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
    OffsetTrackingInputStream inputStream = (OffsetTrackingInputStream) stream;
    long startOffset = inputStream.getOffset();
    assert startOffset % 4 == 0;
    super.read(stream);
    if (myExpectedName != null) {
      String signature = ((WChar) getMember("szKey")).getValue();
      assert signature.equals(myExpectedName): "Expected signature " + myExpectedName + ", found '" + signature + "'";
    }
    if (myChildFactory != null) {
      long length = getValue("wLength");
      int i = 0;
      while(inputStream.getOffset() < startOffset + length) {
        VersionInfoBin child = myChildFactory.createChild(i++);
        child.read(inputStream);
        addMember(child);
      }
    }
  }

  @Override
  public void write(DataOutput stream) throws IOException {
    long startOffset = -1;
    if (stream instanceof RandomAccessFile) {
      startOffset = ((RandomAccessFile) stream).getFilePointer();
      assert startOffset % 4 == 0;
    }
    super.write(stream);
    if (stream instanceof RandomAccessFile) {
      long offset = ((RandomAccessFile) stream).getFilePointer();
      long realLength = offset - startOffset;
      long expectedLength = getValue("wLength");
      assert realLength == expectedLength: "Actual length does not match calculated length for " + getName() +
          ": expected " + expectedLength + ", actual " + realLength + ", sizeInBytes() " + sizeInBytes();
    }
  }
}
