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

import java.io.DataInput;
import java.io.IOException;

/**
 * @author Sergey Zhulin
 * Date: May 10, 2006
 * Time: 1:35:49 PM
 */
public class FixedFileInfo extends Bin.Structure {
  public static final long MAGIC = 0xFEEF04BDL;
  private final DWord mySignature;
  private final DWord myFileVersionMS;
  private final DWord myFileVersionLS;
  private final DWord myProductVersionMS;
  private final DWord myProductVersionLS;

  public FixedFileInfo() {
    super("FixedFileInfo");
    mySignature = addMember(new DWord("dwSignature"));
    addMember( new DWord( "dwStrucVersion" ) );
    myFileVersionMS = addMember(new DWord("dwFileVersionMS"));
    myFileVersionLS = addMember(new DWord("dwFileVersionLS"));
    myProductVersionMS = addMember(new DWord("dwProductVersionMS"));
    myProductVersionLS = addMember(new DWord("dwProductVersionLS"));
    addMember( new DWord( "dwFileFlagsMask" ) );
    addMember( new DWord( "dwFileFlags" ) );
    addMember( new DWord( "dwFileOS" ) );
    addMember( new DWord( "dwFileType" ) );
    addMember( new DWord( "dwFileSubtype" ) );
    addMember( new DWord( "dwFileDateMS" ) );
    addMember( new DWord( "dwFileDateLS" ) );
  }

  @Override
  public void read(DataInput stream) throws IOException {
    super.read(stream);
    long signature = mySignature.getValue();
    if (signature != MAGIC) {
      throw new IllegalStateException(String.format("Incorrect signature; expected %#010x, found %#010x", MAGIC, signature));
    }
  }

  public void setFileVersion(int mostSignificantVersion, int leastSignificantVersion) {
    myFileVersionMS.setValue(mostSignificantVersion);
    myFileVersionLS.setValue(leastSignificantVersion);
  }

  public void setProductVersion(int mostSignificantVersion, int leastSignificantVersion) {
    myProductVersionMS.setValue(mostSignificantVersion);
    myProductVersionLS.setValue(leastSignificantVersion);
  }
}
