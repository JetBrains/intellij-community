package com.pme.exe.res.vi;

import com.pme.exe.Bin;
import com.pme.util.OffsetTrackingInputStream;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author yole
 */
public class VarFileInfo extends Bin.Structure {
  public VarFileInfo() {
    super("VarFileInfo");
    addMember(new Word("wLength"));
    addMember(new Word("wValueLength"));
    addMember(new Word("wType"));
    addMember(new Bytes("szKey", 24));
    addMember(new Padding(4));
  }

  @Override
  public void read(DataInput stream) throws IOException {
    OffsetTrackingInputStream inputStream = (OffsetTrackingInputStream) stream;
    long startOffset = inputStream.getOffset();
    super.read(stream);
    assert ((Bytes) getMember("szKey")).getAsWChar().equals("VarFileInfo");
    long length = getValue("wLength");
    int i = 0;
    while(inputStream.getOffset() < startOffset + length) {
      Var varReader = new Var("Var" + (i++));
      varReader.read(inputStream);
      addMember(varReader);
    }
  }
}
