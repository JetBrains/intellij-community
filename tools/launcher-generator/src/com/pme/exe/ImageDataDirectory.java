// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe;

/**
 * @author Sergey Zhulin
 * Date: Mar 31, 2006
 * Time: 10:40:56 PM
 */
public class ImageDataDirectory extends Bin.Structure {
  public ImageDataDirectory(String name) {
    super(name);
    addMember( new DWord( "VirtualAddress" ) );
    addMember( new DWord( "Size" ) );
  }
  public ImageDataDirectory( ){
    this("");    
  }
}
