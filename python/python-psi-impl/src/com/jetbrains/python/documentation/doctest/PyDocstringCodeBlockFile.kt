// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.doctest

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyExpressionCodeFragment
import com.jetbrains.python.psi.impl.PyFileImpl

open class PyDocstringCodeBlockFile(viewProvider: FileViewProvider?) :
  PyFileImpl(viewProvider, PyDocstringCodeBlockLanguageDialect.getInstance()), PyExpressionCodeFragment {
  override fun getFileType(): FileType {
    return PyDocstringCodeBlockFileType.INSTANCE
  }

  override fun toString(): String {
    return "DocstringCodeBlockFile:$name"
  }

  override fun getLanguageLevel(): LanguageLevel? {
    val languageManager = InjectedLanguageManager.getInstance(project)
    val host = languageManager.getInjectionHost(this)
    if (host != null) return LanguageLevel.forElement(host.getContainingFile())
    return super<PyExpressionCodeFragment>.getLanguageLevel()
  }
}
