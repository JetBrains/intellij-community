// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.requirements

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider


class RequirementsFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, RequirementsLanguage.Companion.INSTANCE) {
  override fun getFileType(): FileType {
    return RequirementsFileType.Companion.INSTANCE
  }

  override fun toString(): String {
    return "Requirements File"
  }
}