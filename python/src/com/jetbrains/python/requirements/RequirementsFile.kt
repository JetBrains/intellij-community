// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.FileViewProvider
import com.jetbrains.python.requirements.psi.Option
import com.jetbrains.python.requirements.psi.Requirement


class RequirementsFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, RequirementsLanguage.INSTANCE) {
  override fun getFileType(): FileType {
    return RequirementsFileType.INSTANCE
  }

  fun requirements(): List<Requirement> {
    val requirements = mutableListOf<Requirement>()
    for (child in this.children) {
      if (child is Requirement) {
        requirements.add(child)
      }
      else if (child is Option) {
        val editableReq = child.editableReq
        val referReq = child.referReq
        if (editableReq != null) {
          requirements.add(editableReq)
        }
        else if (referReq != null) {
          requirements.add(referReq)
        }
      }
    }

    return requirements
  }

  val sdk: Sdk?
    get() {
      return getPythonSdk(this)
    }

  override fun toString(): String {
    return "Requirements File"
  }
}