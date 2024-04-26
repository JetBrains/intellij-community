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
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.resolve.PyCanonicalPathProvider
import com.jetbrains.python.sdk.PythonSdkUtil


class PyStdlibCanonicalPathProvider : PyCanonicalPathProvider {
  override fun getCanonicalPath(symbol: PsiElement?, qName: QualifiedName, foothold: PsiElement?): QualifiedName? {
    val virtualFile = PsiUtilCore.getVirtualFile(symbol)
    if (virtualFile != null && foothold != null) {
      val canonicalPath = restoreStdlibCanonicalPath(qName)
      if (canonicalPath != null && PythonSdkUtil.isStdLib(virtualFile, PythonSdkUtil.findPythonSdk(foothold))) {
        return canonicalPath
      }
    }
    return null
  }
}

private fun QualifiedName.replaceHead(newHead: String): QualifiedName {
  return QualifiedName.fromDottedString(newHead).append(removeHead(1))
}

fun restoreStdlibCanonicalPath(qName: QualifiedName): QualifiedName? {
  if (qName.matchesPrefix(QualifiedName.fromComponents("mock", "mock"))) {
    return qName.removeHead(1)
  }
  return when (qName.firstComponent) {
    "_abcoll", "_collections" -> qName.replaceHead("collections")
    "_collections_abc" -> qName.replaceHead("collections.abc")
    "posix", "nt" -> qName.replaceHead("os")
    "_functools" -> qName.replaceHead("functools")
    "_struct" -> qName.replaceHead("struct")
    "_io", "_pyio", "_fileio" -> qName.replaceHead("io")
    "_datetime" -> qName.replaceHead("datetime")
    "ntpath", "posixpath", "path", "macpath", "os2emxpath", "genericpath" -> qName.replaceHead("os.path")
    "_sqlite3" -> qName.replaceHead("sqlite3")
    "_pickle" -> qName.replaceHead("pickle")
    "_decimal" -> qName.replaceHead("decimal")
    else -> null
  }
}
