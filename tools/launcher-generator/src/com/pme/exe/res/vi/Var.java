package com.pme.exe.res.vi;

import com.pme.exe.Bin;
import com.pme.util.OffsetTrackingInputStream;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author yole
 */
public class Var extends Bin.Structure {
  public Var(String name) {
    super(name);
    addMember(new Word("wLength"));
    addMember(new Word("wValueLength"));
    addMember(new Word("wType"));
    addMember(new Bytes("szKey", 24));
    addMember(new Padding(4));
    addMember(new DWord("Translation"));
  }

  @Override
  public void read(DataInput stream) throws IOException {
    OffsetTrackingInputStream inputStream = (OffsetTrackingInputStream) stream;
    long startOffset = inputStream.getOffset();
    assert startOffset % 4 == 0;
    super.read(stream);
    String varKey = ((Bytes) getMember("szKey")).getAsWChar();
    assert varKey.equals("Translation"): "Expected key name Translation, found " + varKey;
  }
}
