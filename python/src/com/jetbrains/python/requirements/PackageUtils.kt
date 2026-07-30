// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.jetbrains.python.extensions.getSdk
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil

fun getPythonSdk(psiFile: PsiFile): Sdk? {
  return PythonSdkUtil.findPythonSdk(psiFile)
}

fun getPythonSdk(project: Project, virtualFile: VirtualFile): Sdk? {
  val module = ModuleUtil.findModuleForFile(virtualFile, project) ?: return null
  val moduleSdk = module.getSdk() ?: return null
  if (moduleSdk.sdkType is PythonSdkType) {
    return moduleSdk
  }
  return null
}