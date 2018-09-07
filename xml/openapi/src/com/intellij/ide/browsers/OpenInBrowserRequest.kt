// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.Url

abstract class OpenInBrowserRequest @JvmOverloads constructor(open val file: PsiFile, val isForceFileUrlIfNoUrlProvider: Boolean = false) {
  var result: Collection<Url>? = null

  var isAppendAccessToken: Boolean = true

  val virtualFile: VirtualFile?
    get() = file.virtualFile

  val project: Project
    get() = file.project

  abstract val element: PsiElement

  fun isPhysicalFile(): Boolean {
    return file.viewProvider.isPhysical && virtualFile !is LightVirtualFile
  }
}

fun createOpenInBrowserRequest(element: PsiElement, isForceFileUrlIfNoUrlProvider: Boolean = false): OpenInBrowserRequest? {
  val psiFile = runReadAction {
    if (element.isValid) {
      element.containingFile?.let { if (it.virtualFile == null) null else it }
    }
    else {
      null
    }
  } ?: return null
  return object : OpenInBrowserRequest(psiFile, isForceFileUrlIfNoUrlProvider) {
    override val element = element
  }
}