// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.extensions.python

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager


fun VirtualFile.toPsi(project: Project): PsiFileSystemItem? {
  val manager = PsiManager.getInstance(project)
  return if (this.isDirectory) manager.findDirectory(this) else manager.findFile(this)

}