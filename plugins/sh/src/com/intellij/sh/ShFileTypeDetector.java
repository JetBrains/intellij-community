// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.openapi.fileTypes.impl.HashBangFileTypeDetector;

public class ShFileTypeDetector extends HashBangFileTypeDetector {
  public ShFileTypeDetector() {
    super(ShFileType.INSTANCE, "sh");
  }
}
