// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.docstrings

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.ast.docstring.DocStringUtilCore
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDocStringOwner
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.StructuredDocString
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.toolbox.Substring

object DocStringUtil {
  @JvmStatic
  @Deprecated("Use {@link DocStringUtilCore#getDocStringValue(PyAstDocStringOwner)}")
  fun getDocStringValue(owner: PyDocStringOwner): String? {
    return DocStringUtilCore.getDocStringValue(owner)
  }

  /**
   * Attempts to detects docstring format first from given text, next from settings and parses text into corresponding structured docstring.
   *
   * @param text   docstring text *with both quotes and string prefix stripped*
   * @param anchor PSI element that will be used to retrieve docstring format from the containing file or the project module
   * @return structured docstring for one of supported formats or instance of [PlainDocString] if none was recognized.
   * @see DocStringFormat.ALL_NAMES_BUT_PLAIN
   *
   * @see DocStringParser.guessDocStringFormat
   */
  /**
   * Attempts to detect docstring format from given text and parses it into corresponding structured docstring.
   * It's recommended to use more reliable [.parse] that fallbacks to format specified in settings.
   *
   * @param text docstring text *with both quotes and string prefix stripped*
   * @return structured docstring for one of supported formats or instance of [PlainDocString] if none was recognized.
   * @see .parse
   */
  @JvmStatic
  @JvmOverloads
  fun parse(text: String, anchor: PsiElement? = null): StructuredDocString {
    val format = DocStringParser.guessDocStringFormat(text, anchor)
    return parseDocStringContent(format, text)
  }

  /**
   * Attempts to detects docstring format first from the text of given string node, next from settings using given expression as an anchor
   * and parses text into corresponding structured docstring.
   *
   * @param stringLiteral supposedly result of [PyDocStringOwner.getDocStringExpression]
   * @return structured docstring for one of supported formats or instance of [PlainDocString] if none was recognized.
   */
  @JvmStatic
  fun parseDocString(stringLiteral: PyStringLiteralExpression): StructuredDocString {
    return parseDocString(DocStringParser.guessDocStringFormat(stringLiteral.stringValue, stringLiteral), stringLiteral)
  }

  @JvmStatic
  fun parseDocString(
    format: DocStringFormat,
    stringLiteral: PyStringLiteralExpression,
  ): StructuredDocString {
    return parseDocString(format, stringLiteral.stringNodes[0])
  }

  @JvmStatic
  fun parseDocString(format: DocStringFormat, node: ASTNode): StructuredDocString {
    //Preconditions.checkArgument(node.getElementType() == PyTokenTypes.DOCSTRING);
    return DocStringParser.parseDocString(format, node.text)
  }

  /**
   * @param stringContent docstring text without string prefix and quotes, but not escaped, otherwise ranges of [Substring] returned
   * from [StructuredDocString] may be invalid
   */
  @JvmStatic
  fun parseDocStringContent(format: DocStringFormat, stringContent: String): StructuredDocString {
    return DocStringParser.parseDocString(format, Substring(stringContent))
  }

  /**
   * Looks for a doc string under given parent.
   *
   * @param parent where to look. For classes and functions, this would be PyStatementList, for modules, PyFile.
   * @return the defining expression, or null.
   */
  @JvmStatic
  fun findDocStringExpression(parent: PyElement?): PyStringLiteralExpression? {
    return DocStringUtilCore.findDocStringExpression(parent) as PyStringLiteralExpression?
  }

  @JvmStatic
  fun getStructuredDocString(owner: PyDocStringOwner): StructuredDocString? {
    val value = owner.docStringValue
    return if (value == null) null else parse(value, owner)
  }

  /**
   * Returns containing docstring expression of class definition, function definition or module.
   * Useful to test whether particular PSI element is or belongs to such docstring.
   */
  @JvmStatic
  fun getParentDefinitionDocString(element: PsiElement): PyStringLiteralExpression? {
    return DocStringUtilCore.getParentDefinitionDocString(element) as PyStringLiteralExpression?
  }

  @JvmStatic
  fun isDocStringExpression(expression: PyExpression): Boolean {
    if (getParentDefinitionDocString(expression) === expression) {
      return true
    }
    if (expression is PyStringLiteralExpression) {
      return isVariableDocString(expression)
    }
    return false
  }

  @JvmStatic
  fun getAttributeDocComment(attr: PyTargetExpression): String? {
    if (attr.parent is PyAssignmentStatement) {
      val prevSibling: PsiElement? = PyPsiUtils.getPrevNonWhitespaceSibling(attr.parent)
      if (prevSibling is PsiComment && prevSibling.text.startsWith("#:")) {
        return prevSibling.text.substring(2)
      }
    }
    return null
  }

  @JvmStatic
  fun isVariableDocString(expr: PyStringLiteralExpression): Boolean {
    val parent = expr.parent
    if (parent !is PyExpressionStatement) {
      return false
    }
    val prevElement = PyPsiUtils.getPrevNonCommentSibling(parent, true)
    if (prevElement is PyAssignmentStatement) {
      if (expr.text.contains("type:")) return true

      val scope = PsiTreeUtil.getParentOfType(prevElement, ScopeOwner::class.java)
      if (scope is PyClass || scope is PyFile) {
        return true
      }
      if (scope is PyFunction) {
        for (target in prevElement.targets) {
          if (PyUtil.isInstanceAttribute(target)) {
            return true
          }
        }
      }
    }
    return false
  }
}
