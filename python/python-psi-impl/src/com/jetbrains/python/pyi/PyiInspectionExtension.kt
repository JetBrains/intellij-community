/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyImportStatement
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * @author vlan
 */
class PyiInspectionExtension : PyInspectionExtension() {
  override fun ignoreUnused(element: PsiElement, evalContext: TypeEvalContext): Boolean {
    if (element.containingFile !is PyiFile) {
      return false
    }
    val elements = when (element) {
      is PyFromImportStatement -> if (element.isStarImport) emptyList() else element.importElements.toList()
      is PyImportStatement -> element.importElements.toList()
      is PyImportElement -> listOf(element)
      else -> emptyList()
    }
    return elements.isEmpty() || elements.any { it.asName != null }
  }
}
