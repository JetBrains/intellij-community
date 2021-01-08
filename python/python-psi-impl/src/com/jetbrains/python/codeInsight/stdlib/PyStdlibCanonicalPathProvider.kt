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
import java.util.*

/**
 * @author yole
 */
class PyStdlibCanonicalPathProvider : PyCanonicalPathProvider {
  override fun getCanonicalPath(symbol: PsiElement?, qName: QualifiedName, foothold: PsiElement?): QualifiedName? {
    val virtualFile = PsiUtilCore.getVirtualFile(symbol)
    if (virtualFile != null && foothold != null && PythonSdkUtil.isStdLib(virtualFile, PythonSdkUtil.findPythonSdk(foothold))) {
      return restoreStdlibCanonicalPath(qName)
    }
    return null
  }
}

fun replace0(comp0: String, components: ArrayList<String>): QualifiedName {
  val cmp = ArrayList<String>(comp0.split('.'))
  val newComponents = ArrayList<String>(components)
  newComponents.removeAt(0)
  cmp.addAll(newComponents)

  return QualifiedName.fromComponents(cmp)
}

fun restoreStdlibCanonicalPath(qName: QualifiedName): QualifiedName? {
  if (qName.componentCount > 0) {
    val components = ArrayList(qName.components)
    val head = components[0]

    return when (head) {
      "_abcoll", "_collections", "_collections_abc" -> replace0("collections", components)

      "posix", "nt" -> replace0("os", components)
      "_functools" -> replace0("functools", components)
      "_struct" -> replace0("struct", components)
      "_io", "_pyio", "_fileio" -> replace0("io", components)
      "_datetime" -> replace0("datetime", components)
      "ntpath", "posixpath", "path", "macpath", "os2emxpath", "genericpath" -> replace0("os.path", components)
      "_sqlite3" -> replace0("sqlite3", components)
      "_pickle" -> replace0("pickle", components)
      else -> if (qName.matchesPrefix(QualifiedName.fromComponents("mock", "mock"))) {
        return qName.removeHead(1)
      }
      else null
    }
  }
  return null
}
