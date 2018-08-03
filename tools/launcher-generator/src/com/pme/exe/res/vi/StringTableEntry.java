// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res.vi;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Zhulin
 * Date: May 10, 2006
 * Time: 8:26:03 PM
 */
public class StringTableEntry extends VersionInfoBin {
  public StringTableEntry() {
    super("<unnamed>");
    addMember(new WChar("Value"));
    addMember(new Padding(4));
  }

  @Override
  public void read(DataInput stream) throws IOException {
    super.read(stream);
    WChar key = (WChar) getMember("szKey");
    setName(key.getValue());
  }

  @Override
  public String toString() {
    WChar key = (WChar) getMember("szKey");
    WChar value = (WChar) getMember("Value");
    return key.getValue() + " = " + value.getValue();
  }
}
