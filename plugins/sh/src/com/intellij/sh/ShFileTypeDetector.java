// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.HashBangFileTypeDetector;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShFileTypeDetector extends HashBangFileTypeDetector {
  public ShFileTypeDetector() {
    super(ShFileType.INSTANCE, "sh");
  }
}
