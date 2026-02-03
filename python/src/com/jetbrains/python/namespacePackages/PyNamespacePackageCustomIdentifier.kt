// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.namespacePackages

import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyCustomPackageIdentifier

class PyNamespacePackageCustomIdentifier : PyCustomPackageIdentifier {
  init {
    if (!Registry.`is`("python.explicit.namespace.packages")) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun isPackage(directory: PsiDirectory?): Boolean {
    if (directory == null) return false
    if (!Registry.`is`("python.explicit.namespace.packages")) return false
    val module = ModuleUtilCore.findModuleForPsiElement(directory) ?: return false
    return PyNamespacePackagesService.getInstance(module).isNamespacePackage(directory.virtualFile)
  }

  override fun isPackageFile(file: PsiFile): Boolean = false
}