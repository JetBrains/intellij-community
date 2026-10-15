// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.jetbrains.python.configuration.findModuleSdkIfReady
import com.jetbrains.python.sdk.PythonSdkType

/** The SDK of the interpreter of the Python project of [psiFile], read from the snapshot. */
fun getPythonSdk(psiFile: PsiFile): Sdk? {
  return findModuleSdkIfReady(ModuleUtil.findModuleForPsiElement(psiFile))
}

fun getPythonSdk(project: Project, virtualFile: VirtualFile): Sdk? {
  val module = ModuleUtil.findModuleForFile(virtualFile, project) ?: return null
  val moduleSdk = findModuleSdkIfReady(module) ?: return null
  if (moduleSdk.sdkType is PythonSdkType) {
    return moduleSdk
  }
  return null
}