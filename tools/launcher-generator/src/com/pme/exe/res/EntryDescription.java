// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res;

import com.pme.exe.Bin;

/**
 * @author Sergey Zhulin
 * Date: Apr 20, 2006
 * Time: 3:59:15 PM
 */
public class EntryDescription extends Bin.Structure {
  public EntryDescription() {
    super("Entry");
    addMember(new DWord("Name"));
    addMember(new DWord("OffsetToData"));
  }
}
