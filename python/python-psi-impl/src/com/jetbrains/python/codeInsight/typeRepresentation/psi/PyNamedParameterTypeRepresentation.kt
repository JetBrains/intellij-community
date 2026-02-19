/*
 * Copyright 2000-2025 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.typeRepresentation.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.impl.PyElementImpl

class PyNamedParameterTypeRepresentation(astNode: ASTNode) : PyElementImpl(astNode), PsiElement {
  val parameterName: String?
    get() = node.findChildByType(PyTokenTypes.IDENTIFIER)?.text

  val typeExpression: PyExpression?
    get() = findChildByClass(PyExpression::class.java)

  val defaultValue: PyExpression?
    get() {
      // Find the expression after the = token
      val children = node.getChildren(null)
      var foundEquals = false
      for (child in children) {
        if (foundEquals && child.psi is PyExpression) {
          return child.psi as PyExpression
        }
        if (child.elementType == PyTokenTypes.EQ) {
          foundEquals = true
        }
      }
      return null
    }
}
