// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe.res.icon;

import com.pme.exe.Bin;
import com.pme.exe.res.LevelEntry;
import com.pme.exe.res.Level;

/**
 * @author Sergey Zhulin
 * Date: Apr 28, 2006
 * Time: 11:35:48 AM
 */
public class GroupIconResource extends Bin.Structure {
  public GroupIconResource( Bin.Value idCount ) {
    super("GroupIcon");
    addMember(new IconHeader());
    Level level = new Level();
    ArrayOfBins arrayOfBins = new ArrayOfBins("Icon directories", GroupIconResourceDirectory.class, idCount);

    addMember(level);
    Bin[] array = arrayOfBins.getArray();
    for (Bin bin : array) {
      level.addLevelEntry((LevelEntry) bin);
    }

  }
}
