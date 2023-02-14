// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res.vi;


public class Var extends VersionInfoBin {
  public Var(String name) {
    super(name, "Translation");
    addMember(new ArrayOfBins<>("Translation", DWord.class, new ReadOnlyValue("ValueLength/4") {
      @Override
      public long getValue() {
        long size = myValueLength.getValue();
        assert size % 4 == 0;
        return size / 4; // DWord size in bytes
      }
    }));
  }
}
