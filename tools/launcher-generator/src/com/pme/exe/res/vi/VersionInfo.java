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

import com.pme.util.StreamUtil;

import java.io.DataInput;
import java.io.IOException;

public class VersionInfo extends VersionInfoBin {
  private final StringFileInfo myStringFileInfo;
  private final FixedFileInfo myFixedFileInfo;

  public VersionInfo() {
    super("VersionInfo", "VS_VERSION_INFO");
    myFixedFileInfo = addMember(new FixedFileInfo());
    addMember(new Padding("Padding2", 4));
    myStringFileInfo = addMember(new StringFileInfo());
    addMember(new VarFileInfo());
  }

  @Override
  public void read(DataInput stream) throws IOException {
    long startOffset = StreamUtil.getOffset(stream);
    super.read(stream);
    long offset = StreamUtil.getOffset(stream);
    long length = myLength.getValue();
    if (startOffset + length != offset) {
      throw new IOException(String.format("Length specified in version info header %#4x does not match actual version info length %#4x", length, (offset - startOffset)));
    }
  }

  public FixedFileInfo getFixedFileInfo() {
    return myFixedFileInfo;
  }

  public StringFileInfo getStringFileInfo() {
    return myStringFileInfo;
  }
}
