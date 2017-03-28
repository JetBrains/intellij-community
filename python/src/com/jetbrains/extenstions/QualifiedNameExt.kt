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
package com.jetbrains.extenstions

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.resolve.fromModule
import com.jetbrains.python.psi.resolve.resolveModuleAt
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Resolves qname of any symbol to appropriate PSI element.
 */
fun QualifiedName.toElement(module: Module,
                            context: TypeEvalContext,
                            folderToStart: VirtualFile? = null): PsiElement? {
  var currentName = QualifiedName.fromComponents(this.components)


  var element: PsiElement? = null

  // Drill as deep, as we can
  var lastElement: String? = null

  var psiDirectory: PsiDirectory? = null
  val resolveContext = fromModule(module).copyWithMembers()
  if (folderToStart != null) {
    psiDirectory = PsiManager.getInstance(module.project).findDirectory(folderToStart)
  }


  while (currentName.componentCount > 0 && element == null) {
    if (psiDirectory != null) {
      element = resolveModuleAt(currentName, psiDirectory, resolveContext).firstOrNull()
    } else {
      element = resolveQualifiedName(currentName, resolveContext).firstOrNull()
    }

    if (element != null) {
      break
    }
    lastElement = currentName.lastComponent!!
    currentName = currentName.removeLastComponent()
  }

  if (lastElement != null && element is PyClass) {
    // Drill in class
    val method = element.findMethodByName(lastElement, true, context)
    if (method != null) {
      return method
    }

  }
  return element
}