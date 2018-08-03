// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res.vi;

/**
 * @author Sergey Zhulin
 * Date: May 10, 2006
 * Time: 8:01:15 PM
 */
public class StringFileInfo extends VersionInfoBin {
  public StringFileInfo() {
    super("StringFileInfo", "StringFileInfo", new VersionInfoFactory() {
      @Override
      public VersionInfoBin createChild(int index) {
        return new StringTable("StringTable" + index);
      }
    });
  }

  public StringTable getFirstStringTable() {
    return (StringTable) getMember("StringTable0");
  }
}
