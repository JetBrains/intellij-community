/*
 * Copyright 2006 ProductiveMe Inc.
 * Copyright 2013-2022 JetBrains s.r.o.
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

package com.pme.exe.res.vi;

import com.pme.exe.Bin;

import java.util.Optional;

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

  public StringTable getSoleStringTable() {
    long count = getMembers().stream().filter(bin -> bin instanceof StringTable).count();
    if (count > 1) {
      throw new IllegalStateException("More than one StringTable found, indicates that there's more than one lanugage in executable");
    }
    Optional<Bin> optional = getMembers().stream().filter(bin -> bin instanceof StringTable).findFirst();
    if (optional.isEmpty()) {
      throw new IllegalStateException("No StringTable's found");
    }
    return (StringTable)optional.get();
  }
}
