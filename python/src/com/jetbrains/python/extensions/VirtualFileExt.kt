// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.extensions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.PsiUtilCore


fun VirtualFile.toPsi(project: Project): PsiFileSystemItem? {
  return PsiUtilCore.findFileSystemItem(project, this)
}