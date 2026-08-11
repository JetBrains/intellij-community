/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.documentation.doctest

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyExpressionCodeFragment
import com.jetbrains.python.psi.impl.PyFileImpl

open class PyDoctestFile(viewProvider: FileViewProvider?) : PyFileImpl(viewProvider, PyDoctestLanguageDialect.getInstance()),
                                                            PyExpressionCodeFragment {
  override fun getFileType(): FileType {
    return PyDoctestFileType.INSTANCE
  }

  override fun toString(): String {
    return "DoctestFile:$name"
  }

  override fun getLanguageLevel(): LanguageLevel? {
    val languageManager = InjectedLanguageManager.getInstance(project)
    val host = languageManager.getInjectionHost(this)
    if (host != null) return LanguageLevel.forElement(host.getContainingFile())
    return super<PyExpressionCodeFragment>.getLanguageLevel()
  }
}