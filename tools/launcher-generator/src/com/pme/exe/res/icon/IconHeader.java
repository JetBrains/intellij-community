// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res.icon;

import com.pme.exe.Bin;

/**
 * @author Sergey Zhulin
 * Date: Apr 28, 2006
 * Time: 11:30:10 AM
 */
public class IconHeader extends Bin.Structure{
  public IconHeader() {
    super("Header");
    addMember(new Word("idReserved"));
    addMember(new Word("idType"));
    addMember(new Word("idCount"));
  }
}
