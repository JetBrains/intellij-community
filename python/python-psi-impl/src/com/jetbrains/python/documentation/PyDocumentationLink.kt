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
import com.jetbrains.python.documentation.PyDocumentationLink.toPossibleClass
import com.jetbrains.python.PyNames
import com.jetbrains.python.highlighting.PyHighlighter
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyPsiFacade
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyTypeAliasStatement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTypeParser
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isNoneType
import org.jetbrains.annotations.Nls

object PyDocumentationLink {

  /** Prefix understood by `com.intellij.codeInsight.hint.ElementLinkHandler` for navigable tooltip links. */
  private const val TOOLTIP_ELEMENT_LINK_PREFIX = "#element/"

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
  fun toPossibleClass(typeName: @Nls String, anchor: PsiElement, context: TypeEvalContext): HtmlChunk {
    val type = resolveNamedClassType(typeName, anchor, context)
    return when {
      type != null -> {
        val text = toClass(type.pyClass, typeName)
        if (type.isNoneType)
          styledSpan(text, PyHighlighter.PY_KEYWORD)
        else
          styledReference(text, type.pyClass)
      }
      else -> HtmlChunk.text(typeName)
    }
  }

  /**
   * Resolves [typeName] to a single [PyClassType]. When the numeric tower is enabled, parsing a name such as
   * `float` yields a `float | int` union rather than a bare class type (see `PyNumericTowerUtil`); in that case
   * the union member whose name matches [typeName] is returned, so the rendered name still links to its own class
   * instead of falling back to plain, unhighlighted text.
   */
  private fun resolveNamedClassType(typeName: String, anchor: PsiElement, context: TypeEvalContext): PyClassType? {
    return when (val type = PyTypeParser.getTypeByName(anchor, typeName, context)) {
      is PyClassType -> type
      is PyUnionType -> type.members.filterIsInstance<PyClassType>().firstOrNull { it.name == typeName }
      else -> null
    }
  }

  /**
   * Like [toPossibleClass], but produces a link in the {@code #element/<fqn>} format understood by the editor
   * tooltip link handler (see {@code com.intellij.codeInsight.hint.ElementLinkHandler}) rather than the
   * {@code psi_element://} protocol used by Quick Documentation. Use this to render clickable, highlighted
   * type names inside inspection tooltips.
   *
   * The same highlighting as [toPossibleClass] is applied, so builtins keep their builtin color. When the
   * resolved class has no qualified name (e.g. a local class) the name is rendered as highlighted text
   * without a link, so it can still be navigated to visually even if it cannot be resolved from the tooltip.
   */
  @JvmStatic
  fun toPossibleClassTooltipLink(typeName: @Nls String, anchor: PsiElement, context: TypeEvalContext): HtmlChunk {
    val type = resolveNamedClassType(typeName, anchor, context)
    return when {
      type != null -> {
        val qualifiedName = type.pyClass.qualifiedName
        val text = if (!qualifiedName.isNullOrEmpty())
          HtmlChunk.link("$TOOLTIP_ELEMENT_LINK_PREFIX$qualifiedName", typeName)
        else
          HtmlChunk.text(typeName)
        if (type.isNoneType)
          styledSpan(text, PyHighlighter.PY_KEYWORD)
        else
          styledReference(text, type.pyClass)
      }
      else -> HtmlChunk.text(typeName)
    }
  }

  @JvmStatic
  fun toClass(pyClass: PyClass, linkText: @Nls String): HtmlChunk {
    return toClass(pyClass.qualifiedName.orEmpty(), linkText)
  }

  /**
   * Renders [linkText] as a tooltip-navigable link (`#element/<fqn>`, see
   * [com.intellij.codeInsight.hint.ElementLinkHandler]) pointing at [target], for use in inspection tooltips.
   * Falls back to plain text when [target] has no qualified name (e.g. a local class), so it still renders but
   * is simply not clickable.
   */
  @JvmStatic
  fun toElementTooltipLink(target: PyQualifiedNameOwner, linkText: @Nls String): HtmlChunk {
    val qualifiedName = target.qualifiedName
    return if (qualifiedName.isNullOrEmpty()) HtmlChunk.text(linkText)
    else HtmlChunk.link("$TOOLTIP_ELEMENT_LINK_PREFIX$qualifiedName", linkText)
  }

  @JvmStatic
  fun toClass(qualifiedName: String, linkText: @Nls String): HtmlChunk {
    val linkTarget = PyNames.FQN.unqualifyBuiltinName(qualifiedName)!!
    return HtmlChunk.link("${DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL}$LINK_TYPE_TYPENAME$linkTarget", linkText)
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
  fun toTypeAliasStatement(@NlsSafe content: String, typeAliasStatement: PyTypeAliasStatement): HtmlChunk {
    val qualifiedName = typeAliasStatement.qualifiedName
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
    return resolveNamedClassType(type, anchor, context)?.pyClass
  }
}
