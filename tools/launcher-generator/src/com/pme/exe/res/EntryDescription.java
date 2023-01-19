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

package com.pme.exe.res;

import com.pme.exe.Bin;

/**
 * @author Sergey Zhulin
 * Date: Apr 20, 2006
 * Time: 3:59:15 PM
 */
public class EntryDescription extends Bin.Structure {
  private final DWord myNameW;
  private final DWord myOffsetToData;

  public EntryDescription() {
    super("Entry");
    addMember(myNameW = new DWord("Name"));
    addMember(myOffsetToData = new DWord("OffsetToData"));
  }

  public DWord getNameW() {
    return myNameW;
  }

  public DWord getOffsetToData() {
    return myOffsetToData;
  }
}
