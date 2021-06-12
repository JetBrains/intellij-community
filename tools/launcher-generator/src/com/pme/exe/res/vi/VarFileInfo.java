// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res.vi;


public class VarFileInfo extends VersionInfoBin {
  public VarFileInfo() {
    super("VarFileInfo", "VarFileInfo", new VersionInfoFactory() {
      @Override
      public VersionInfoBin createChild(int index) {
        return new Var("Var" + index);
      }
    });
  }
}
