// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PythonFileType;


public final class PyProblemFileHighlightFilter implements Condition<VirtualFile> {
  @Override
  public boolean value(VirtualFile virtualFile) {
    final FileType fileType = virtualFile.getFileType();
    return fileType == PythonFileType.INSTANCE;
  }
}
