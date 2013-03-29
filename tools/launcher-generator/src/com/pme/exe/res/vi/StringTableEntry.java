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

import java.io.DataInput;
import java.io.IOException;

public class StringTableEntry extends VersionInfoBin {
  public StringTableEntry() {
    super("<unnamed>");
    addMember(new WChar("Value"));
    addMember(new Padding(4));
  }

  @Override
  public void read(DataInput stream) throws IOException {
    super.read(stream);
    WChar key = (WChar) getMember("szKey");
    setName(key.getValue());
  }

  @Override
  public String toString() {
    WChar key = (WChar) getMember("szKey");
    WChar value = (WChar) getMember("Value");
    return key.getValue() + " = " + value.getValue();
  }
}
