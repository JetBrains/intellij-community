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

import java.io.DataInput;
import java.io.IOException;

/**
 * Date: May 10, 2006
 * Time: 1:35:49 PM
 */
public class FixedFileInfo extends Bin.Structure {
  public static final String FILE_VERSION_MS = "dwFileVersionMS";
  public static final String FILE_VERSION_LS = "dwFileVersionLS";
  public static final String PRODUCT_VERSION_MS = "dwProductVersionMS";
  public static final String PRODUCT_VERSION_LS = "dwProductVersionLS";

  public FixedFileInfo() {
    super("FixedFileInfo");
    addMember( new DWord( "dwSignature" ) );
    addMember( new DWord( "dwStrucVersion" ) );
    addMember( new DWord(FILE_VERSION_MS) );
    addMember( new DWord(FILE_VERSION_LS) );
    addMember( new DWord(PRODUCT_VERSION_MS) );
    addMember( new DWord(PRODUCT_VERSION_LS) );
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
    long signature = getValue("dwSignature");
    assert signature == 0xFEEF04BDl : "Incorrect signature; expected " +0xFEEF04BDl + ", found " + signature;
  }

  public void setFileVersion(int mostSignificantVersion, int leastSignificantVersion) {
    ((DWord) getMember(FILE_VERSION_MS)).setValue(mostSignificantVersion);
    ((DWord) getMember(FILE_VERSION_LS)).setValue(leastSignificantVersion);
  }

  public void setProductVersion(int mostSignificantVersion, int leastSignificantVersion) {
    ((DWord) getMember(PRODUCT_VERSION_MS)).setValue(mostSignificantVersion);
    ((DWord) getMember(PRODUCT_VERSION_LS)).setValue(leastSignificantVersion);
  }
}
