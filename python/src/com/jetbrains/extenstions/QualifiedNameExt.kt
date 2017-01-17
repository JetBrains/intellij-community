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
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.impl.PyPsiFacadeImpl
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Resolves qname of any symbol to appropriate PSI element.
 */
fun QualifiedName.toElement(module: Module, context: TypeEvalContext): PsiElement? {
  val facade = PyPsiFacadeImpl.getInstance(module.project)
  var currentName = this


  var element: PsiElement? = null

  // Drill as deep, as we can
  var lastElement: String? = null
  while (currentName.componentCount > 0 && element == null) {

    element = facade.qualifiedNameResolver(currentName).fromModule(module).withMembers().firstResult()
    if (element != null) {
      break
    }
    lastElement = this.lastComponent!!
    currentName = this.removeLastComponent()
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