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

package com.pme.exe;

/**
 * @author Sergey Zhulin
 * Date: Mar 31, 2006
 * Time: 10:40:56 PM
 */
public class ImageDataDirectory extends Bin.Structure {
  public static final int IMAGE_DIRECTORY_ENTRY_RESOURCE = 2;
  @SuppressWarnings("SpellCheckingInspection")
  public static final int IMAGE_DIRECTORY_ENTRY_BASERELOC = 5;

  private final DWord myVirtualAddress;
  private final DWord mySize;

  public ImageDataDirectory(String name) {
    super(name);
    myVirtualAddress = addMember(new DWord("VirtualAddress"));
    mySize = addMember(new DWord("Size"));
  }

  // Used by Bin.ArrayOfBins
  @SuppressWarnings("unused")
  public ImageDataDirectory() {
    this("");
  }

  public DWord getVirtualAddress() {
    return myVirtualAddress;
  }

  public DWord getSize() {
    return mySize;
  }
}
