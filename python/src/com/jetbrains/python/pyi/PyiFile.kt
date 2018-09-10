/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.pyi

import com.intellij.psi.FileViewProvider
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.impl.PyFileImpl
import com.jetbrains.python.psi.resolve.ImportedResolveResult
import com.jetbrains.python.psi.resolve.RatedResolveResult

/**
 * @author vlan
 */
class PyiFile(viewProvider: FileViewProvider) : PyFileImpl(viewProvider, PyiLanguageDialect.getInstance()) {
  override fun getFileType(): PythonFileType = PyiFileType.INSTANCE

  override fun toString(): String = "PyiFile:" + name

  override fun getLanguageLevel(): LanguageLevel = LanguageLevel.PYTHON37

  override fun multiResolveName(name: String, exported: Boolean): List<RatedResolveResult> {
    val baseResults = super.multiResolveName(name, exported)
    return if (exported)
      baseResults
        .filter {
          val importedResult = it as? ImportedResolveResult
          val importElement = importedResult?.definer as? PyImportElement
          importElement == null || importElement.asName != null
        }
    else
      baseResults
  }
}
