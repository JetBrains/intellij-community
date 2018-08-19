/*
 * Copyright 2006 ProductiveMe Inc.
 * Copyright 2013-2018 JetBrains s.r.o.
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

/**
 * @author Sergey Zhulin
 * Date: Apr 28, 2006
 * Time: 11:38:20 AM
 */
public class GroupIconResourceDirectory extends LevelEntry {
  public GroupIconResourceDirectory() {
    super("Icon Directory");
    addMember( new Byte( "bWidth" ) );
    addMember( new Byte( "bHeight" ) );
    addMember( new Byte( "bColorCount" ) );
    addMember( new Byte( "bReserved" ) );
    addMember( new Word( "wPlanes" ) );
    addMember( new Word( "wBitCount" ) );
    addMember( new DWord( "dwBytesInRes" ) );
    addMember( new Word( "dwId" ) );
  }
}
