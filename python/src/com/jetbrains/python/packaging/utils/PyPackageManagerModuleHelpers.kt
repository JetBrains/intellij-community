// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.utils

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiDirectory
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.psi.PyExpression

object PyPackageManagerModuleHelpers {
  fun isRunningPackagingTasks(module: Module): Boolean {
    val value = module.getUserData(PythonPackageManager.RUNNING_PACKAGING_TASKS)
    return value != null && value
  }

  fun isLocalModule(packageReferenceExpression: PyExpression, module: Module): Boolean {
    val reference = packageReferenceExpression.reference ?: return false
    val element = reference.resolve() ?: return false

    if (element is PsiDirectory) {
      return ModuleUtilCore.moduleContainsFile(module, element.virtualFile, false)
    }

    val file = element.containingFile ?: return false
    val virtualFile = file.virtualFile ?: return false
    return ModuleUtilCore.moduleContainsFile(module, virtualFile, false)
  }
}