// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;

public class BuildoutCfgProblemFileHighlightFilter implements Condition<VirtualFile> {
  @Override
  public boolean value(VirtualFile virtualFile) {
    final FileType fileType = virtualFile.getFileType();
    return fileType == BuildoutCfgFileType.INSTANCE;
  }
}
