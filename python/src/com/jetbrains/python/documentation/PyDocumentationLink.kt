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
package com.jetbrains.python.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTypeParser
import com.jetbrains.python.psi.types.TypeEvalContext

object PyDocumentationLink {

  private const val LINK_TYPE_CLASS = "#class#"
  private const val LINK_TYPE_PARAM = "#param#"
  private const val LINK_TYPE_TYPENAME = "#typename#"

  @JvmStatic
  fun toContainingClass(content: String?): String {
    return "<a href=\"${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}$LINK_TYPE_CLASS\">$content</a>"
  }

  @JvmStatic
  fun toParameterPossibleClass(type: String, anchor: PsiElement, context: TypeEvalContext): String {
    val pyType = PyTypeParser.getTypeByName(anchor, type, context)
    return when (pyType) {
      is PyClassType -> "<a href=\"${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}$LINK_TYPE_PARAM\">$type</a>"
      else -> type
    }
  }

  @JvmStatic
  fun toPossibleClass(type: String, anchor: PsiElement, context: TypeEvalContext): String = toPossibleClass(type, type, anchor, context)

  @JvmStatic
  fun toPossibleClass(content: String, qualifiedName: String, anchor: PsiElement, context: TypeEvalContext): String {
    val pyType = PyTypeParser.getTypeByName(anchor, qualifiedName, context)
    return when (pyType) {
      is PyClassType -> "<a href=\"${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}$LINK_TYPE_TYPENAME$qualifiedName\">$content</a>"
      else -> content
    }
  }

  @JvmStatic
  fun elementForLink(link: String, element: PsiElement, context: TypeEvalContext): PsiElement? {
    return if (link == LINK_TYPE_CLASS) {
      containingClass(element)
    }
    else if (link == LINK_TYPE_PARAM) {
      parameterPossibleClass(element, context)
    }
    else if (link.startsWith(LINK_TYPE_TYPENAME)) {
      possibleClass(link.substring(LINK_TYPE_TYPENAME.length), element, context)
    }
    else {
      null
    }
  }

  @JvmStatic
  private fun containingClass(element: PsiElement): PyClass? {
    return when (element) {
      is PyClass -> element
      is PyFunction -> element.containingClass
      else -> PsiTreeUtil.getParentOfType(element, PyClass::class.java)
    }
  }

  @JvmStatic
  private fun parameterPossibleClass(parameter: PsiElement, context: TypeEvalContext): PyClass? {
    if (parameter is PyNamedParameter) {
      val type = context.getType(parameter)
      if (type is PyClassType) {
        return type.pyClass
      }
    }
    return null
  }

  @JvmStatic
  private fun possibleClass(type: String, anchor: PsiElement, context: TypeEvalContext): PyClass? {
    val pyType = PyTypeParser.getTypeByName(anchor, type, context)
    return when (pyType) {
      is PyClassType -> pyType.pyClass
      else -> null
    }
  }
}