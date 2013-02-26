/*
 * Copyright 2006 ProductiveMe Inc.
 * Copyright 2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pme.exe.res.icon;

import com.pme.exe.Bin;
import com.pme.exe.res.LevelEntry;
import com.pme.exe.res.Level;

/**
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
