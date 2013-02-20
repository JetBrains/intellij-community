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

package com.pme.exe.res.bmp;

import com.pme.exe.Bin;

/**
 * Date: May 3, 2006
 * Time: 12:34:47 PM
 */
public class BitmapFileHeader extends Bin.Structure{
  public BitmapFileHeader() {
    super("Bitmap File Header");
    addMember( new Word( "bfType" ) );
    addMember( new DWord( "bfSize" ) );
    addMember( new Word( "bfReserved1" ) );
    addMember( new Word( "bfReserved2" ) );
    addMember( new DWord( "bfOffBits" ) );
  }
}
