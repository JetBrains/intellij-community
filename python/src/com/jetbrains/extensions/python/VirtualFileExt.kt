// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.extensions.python

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonFileType

fun VirtualFile.isPythonFile() = PythonFileType.INSTANCE == FileTypeRegistry.getInstance().getFileTypeByFileName(name)
fun VirtualFile.isPythonPackage() = isDirectory && findChild(PyNames.INIT_DOT_PY) != null
/**
 * @return pyfile or package
 */
fun VirtualFile.isPythonModule() = isPythonPackage() || isPythonFile()
/**
 * @return psi element if and only if vfs item is python module
 */
fun VirtualFile.toPythonPsi(project: Project): PsiFileSystemItem? {
  if (!isPythonModule()) return null
  val manager = PsiManager.getInstance(project)
  return if (this.isDirectory) manager.findDirectory(this) else manager.findFile(this)

}