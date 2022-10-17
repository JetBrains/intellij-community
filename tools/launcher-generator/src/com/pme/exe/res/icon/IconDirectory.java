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

package com.pme.exe.res.icon;

/**
 * @author Sergey Zhulin
 * Date: Apr 27, 2006
 * Time: 1:13:15 PM
 */
public class IconDirectory extends IconBase {
  private final Bytes myBytes;

  public IconDirectory() {
    super("Icon Directory");
    DWord dwImageOffset = addMember(new DWord("dwImageOffset"));
    myBytes = new Bytes("Raw Bytes internal", dwImageOffset, myDwBytesInRes);
    myBytes.addOffsetHolder(dwImageOffset);
    myBytes.addSizeHolder(myDwBytesInRes);
  }

  public Bytes getBytes() {
    return myBytes;
  }
}
