// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.pyi

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyCustomPackageIdentifier
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyiCustomPackageIdentifier : PyCustomPackageIdentifier {
  override fun isPackage(directory: PsiDirectory): Boolean = directory.findFile(PyNames.INIT_DOT_PYI) != null

  override fun isPackageFile(file: PsiFile): Boolean = file.name == PyNames.INIT_DOT_PYI
}
