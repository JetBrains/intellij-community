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
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTypeParser
import com.jetbrains.python.psi.types.TypeEvalContext

object PyDocumentationLink {

  private const val LINK_TYPE_CLASS = "#class#"
  private const val LINK_TYPE_PARAM = "#param#"
  private const val LINK_TYPE_TYPENAME = "#typename#"
  private const val LINK_TYPE_FUNC = "#func#"
  private const val LINK_TYPE_MODULE = "#module#"

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
  fun toPossibleClass(type: String, anchor: PsiElement, context: TypeEvalContext) = toPossibleClass(type, type, anchor, context)

  @JvmStatic
  fun toPossibleClass(content: String, qualifiedName: String, anchor: PsiElement, context: TypeEvalContext): String {
    val pyType = PyTypeParser.getTypeByName(anchor, qualifiedName, context)
    return when (pyType) {
      is PyClassType -> "<a href=\"${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}$LINK_TYPE_TYPENAME$qualifiedName\">$content</a>"
      else -> content
    }
  }

  @JvmStatic
  fun toFunction(func: PyFunction): String = toFunction(func.qualifiedName ?: func.name.orEmpty(), func)

  @JvmStatic
  fun toFunction(content: String, func: PyFunction): String {
    val qualifiedName = func.qualifiedName
    return when {
      qualifiedName != null -> "<a href=\"${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}$LINK_TYPE_FUNC$qualifiedName\">$content</a>"
      else -> content
    }
  }

  @JvmStatic
  fun toModule(content: String, qualifiedName: String): String {
    return "<a href=\"${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}$LINK_TYPE_MODULE$qualifiedName\">$content</a>"
  }

  @JvmStatic
  fun elementForLink(link: String, element: PsiElement, context: TypeEvalContext): PsiElement? {
    return when {
      link == LINK_TYPE_CLASS -> containingClass(element)
      link == LINK_TYPE_PARAM -> parameterPossibleClass(element, context)
      link.startsWith(LINK_TYPE_TYPENAME) -> possibleClass(link.substring(LINK_TYPE_TYPENAME.length), element, context)
      link.startsWith(LINK_TYPE_FUNC) -> possibleFunction(link.substring(LINK_TYPE_FUNC.length), element)
      link.startsWith(LINK_TYPE_MODULE) -> possibleModule(link.substring(LINK_TYPE_MODULE.length), element)
      else -> null
    }
  }

  private fun possibleModule(qualifiedName: String, element: PsiElement): PyFile? {
    val facade = PyPsiFacade.getInstance(element.project)
    val qName = QualifiedName.fromDottedString(qualifiedName)

    val resolveContext = facade.createResolveContextFromFoothold(element)
    return facade.resolveQualifiedName(qName, resolveContext)
      .filterIsInstance<PsiFileSystemItem>()
      .map { PyUtil.turnDirIntoInit(it) }
      .filterIsInstance<PyFile>()
      .firstOrNull()
  }

  @JvmStatic
  private fun possibleFunction(qualifiedName: String, element: PsiElement): PyFunction? {
    // TODO a better, more general way to resolve qualified names of function
    val facade = PyPsiFacade.getInstance(element.project)
    val qName = QualifiedName.fromDottedString(qualifiedName)

    val resolveContext = facade.createResolveContextFromFoothold(element).copyWithMembers()
    val topLevel = facade.resolveQualifiedName(qName, resolveContext).filterIsInstance<PyFunction>().firstOrNull()
    if (topLevel != null) {
      return topLevel
    }

    return facade.resolveQualifiedName(qName.removeLastComponent(), resolveContext)
      .asSequence()
      .filterIsInstance<PyClass>()
      .map { it.findMethodByName(qName.lastComponent, false, null) }
      .firstOrNull()
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