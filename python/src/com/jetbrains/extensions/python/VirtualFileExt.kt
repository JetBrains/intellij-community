// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.extensions.python

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.PsiUtilCore


fun VirtualFile.toPsi(project: Project): PsiFileSystemItem? {
  return PsiUtilCore.findFileSystemItem(project, this)
}