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

package com.pme.exe.res.vi;

import com.pme.exe.Bin;

public class StringTable extends VersionInfoBin {

  public StringTable(String name) {
    super(name, null, new VersionInfoFactory() {
      @Override
      public VersionInfoBin createChild(int index) {
        return new StringTableEntry();
      }
    });
  }

  public void setStringValue(String key, String value) {
    for (Bin bin : getMembers()) {
      if (bin.getName().equals(key)) {
        StringTableEntry entry = (StringTableEntry) bin;
        ((WChar) entry.getMember("Value")).setValue(value);
        ((Word) entry.getMember("wValueLength")).setValue(value.length());
        return;
      }
    }
    assert false: "Could not find string with key " + key;
  }
}
