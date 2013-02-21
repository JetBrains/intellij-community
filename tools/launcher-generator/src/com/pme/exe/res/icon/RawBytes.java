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

import com.pme.exe.res.LevelEntry;
import com.pme.exe.Bin;

/**
 * Date: Apr 27, 2006
 * Time: 1:44:11 PM
 */
public class RawBytes extends LevelEntry {
  public RawBytes( Bin.Value offsetHolder, Bin.Value size ) {
    super("Raw Bytes");
    Bytes bytes = new Bytes("Raw Bytes", offsetHolder, size);
    bytes.addOffsetHolder( offsetHolder );
    bytes.addSizeHolder( size );
    addMember( bytes );
  }
}
