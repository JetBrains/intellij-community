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
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.highlighting.PyHighlighter
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyNoneType
import com.jetbrains.python.psi.types.PyTypeParser
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.Nls

object PyDocumentationLink {

  private const val LINK_TYPE_CLASS = "#class#"
  private const val LINK_TYPE_PARAM = "#param#"
  private const val LINK_TYPE_TYPENAME = "#typename#"
  private const val LINK_TYPE_FUNC = "#func#"
  private const val LINK_TYPE_MODULE = "#module#"

  @JvmStatic
  fun toContainingClass(@NlsSafe content: String): HtmlChunk {
    return HtmlChunk.link("${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}$LINK_TYPE_CLASS", content)
  }

  @JvmStatic
  fun toParameterPossibleClass(@NlsSafe type: String, anchor: PsiElement, context: TypeEvalContext): HtmlChunk {
    return when (PyTypeParser.getTypeByName(anchor, type, context)) {
      is PyClassType -> HtmlChunk.link("${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}$LINK_TYPE_PARAM", type)
      else -> HtmlChunk.text(type)
    }
  }

  @JvmStatic
  fun toPossibleClass(typeName: @Nls String, anchor: PsiElement, context: TypeEvalContext): HtmlChunk =
    when (val type = PyTypeParser.getTypeByName(anchor, typeName, context)) {
      is PyClassType -> styledReference(toClass(type.pyClass, typeName), type.pyClass)
      is PyNoneType -> styledSpan(typeName, PyHighlighter.PY_KEYWORD)
      else -> HtmlChunk.text(typeName)
    }

  @JvmStatic
  fun toClass(pyClass: PyClass, linkText: @Nls String): HtmlChunk {
    val qualifiedName = pyClass.qualifiedName
    return HtmlChunk.link("${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}$LINK_TYPE_TYPENAME$qualifiedName", linkText)
  }

  @JvmStatic
  fun toFunction(@NlsSafe content: String, func: PyFunction): HtmlChunk {
    val qualifiedName = func.qualifiedName
    return when {
      qualifiedName != null -> HtmlChunk.link("${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}$LINK_TYPE_FUNC$qualifiedName", content)
      else -> HtmlChunk.text(content)
    }
  }

  @JvmStatic
  fun toModule(@NlsSafe content: String, qualifiedName: String): HtmlChunk {
    return HtmlChunk.link("${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}$LINK_TYPE_MODULE$qualifiedName", content)
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
    return when (val pyType = PyTypeParser.getTypeByName(anchor, type, context)) {
      is PyClassType -> pyType.pyClass
      else -> null
    }
  }
}
