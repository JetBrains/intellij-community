// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res;

import com.pme.exe.Bin;

/**
 * @author Sergey Zhulin
 * Date: Apr 17, 2006
 * Time: 11:02:21 PM
 */
public abstract class LevelEntry extends Bin.Structure {
  private Level myLevel = null;

  public LevelEntry(String name) {
    super(name);
  }

  public void setLevel(Level level) {
    myLevel = level;
  }

  public Level getLevel() {
    return myLevel;
  }

}
