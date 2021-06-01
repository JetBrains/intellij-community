// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res.vi;


public class Var extends VersionInfoBin {
  public Var(String name) {
    super(name, "Translation");
    addMember(new DWord("Translation"));
  }
}
