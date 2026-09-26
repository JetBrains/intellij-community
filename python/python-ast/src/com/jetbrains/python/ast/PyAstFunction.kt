// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyElementTypes
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonDialectsTokenSetProvider
import com.jetbrains.python.ast.controlFlow.AstScopeOwner
import com.jetbrains.python.ast.docstring.DocStringUtilCore
import com.jetbrains.python.ast.impl.PyUtilCore
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus

/**
 * Function declaration in source (the {@code def} and everything within).
 */
@ApiStatus.Experimental
interface PyAstFunction : PsiNameIdentifierOwner, PyAstCompoundStatement,
                          PyAstDecoratable, PyAstCallable, PyAstStatementListContainer, PyAstPossibleClassMember,
                          AstScopeOwner, PyAstDocStringOwner, PyAstTypeCommentOwner, PyAstAnnotationOwner, PyAstTypeParameterListOwner {

  companion object {
    private fun isRaiseNotImplementedError(statement: PyAstStatement): Boolean {
      val raisedExpression =
        (statement as? PyAstRaiseStatement)
          ?.expressions
          ?.takeIf { it.size == 1 }
          ?.first()
        ?: return false

      return when (raisedExpression) {
        is PyAstCallExpression -> raisedExpression.callee
        else -> raisedExpression
      }?.text == PyNames.NOT_IMPLEMENTED_ERROR
    }
  }

  override fun getName(): String? =
    nameNode?.text

  override fun getNameIdentifier(): PsiElement? =
    nameNode?.psi

  /**
   * Returns the AST node for the function name identifier.
   *
   * @return the node, or null if the function is incomplete (only the "def"
   *         keyword was typed)
   */
  val nameNode: ASTNode?
    get() = node.findChildByType(PyTokenTypes.IDENTIFIER)
          ?: node.findChildByType(TokenType.ERROR_ELEMENT)?.findChildByType(PythonDialectsTokenSetProvider.getInstance().keywordTokens)

  override fun getStatementList(): PyAstStatementList =
    requireNotNull(childToPsi(PyElementTypes.STATEMENT_LIST)) { "Statement list missing for function $text" }

  override fun asMethod(): PyAstFunction? =
    this.takeIf { containingClass != null }

  override fun getDocStringValue(): String? =
    DocStringUtilCore.getDocStringValue(this)

  override fun getTextOffset(): Int =
    nameNode?.startOffset ?: node.startOffset

  override fun getDocStringExpression(): PyAstStringLiteralExpression? =
    DocStringUtilCore.findDocStringExpression(statementList)

  override fun getTypeParameterList(): PyAstTypeParameterList?

  /**
   * Looks for two standard decorators to a function, or a wrapping assignment that closely follows it.
   *
   * @return a flag describing what was detected.
   */
  val modifier: Modifier?

  val isAsync: Boolean
    get() = node.findChildByType(PyTokenTypes.ASYNC_KEYWORD) != null

  val isAsyncAllowed: Boolean
    get() {
      val languageLevel = LanguageLevel.forElement(this)
      if (languageLevel.isOlderThan(LanguageLevel.PYTHON35)) return false

      val functionName = name

      if (name in setOf(PyNames.AITER, PyNames.ANEXT, PyNames.AENTER, PyNames.AEXIT, PyNames.CALL)) {
        return true
      }

      val builtinMethods =
        if (asMethod() != null) PyNames.getBuiltinMethods(languageLevel) else PyNames.getModuleBuiltinMethods(languageLevel)

      return functionName !in builtinMethods
    }

  fun onlyRaisesNotImplementedError(): Boolean {
    val statements = statementList.statements
    return statements.size == 1 && isRaiseNotImplementedError(statements[0]) ||
           statements.size == 2 && PyUtilCore.isStringLiteral(statements[0]) && isRaiseNotImplementedError(statements[1])
  }

  /**
   * Flags that mark common alterations of a function: decoration by and wrapping in classmethod() and staticmethod().
   */
  enum class Modifier {
    /**
     * Function is decorated with @classmethod, its first param is the class.
     */
    CLASSMETHOD,

    /**
     * Function is decorated with {@code @staticmethod}, its first param is as in a regular function.
     */
    STATICMETHOD;

    val decoratorName: String get() = when (this) {
      CLASSMETHOD -> PyNames.CLASSMETHOD
      STATICMETHOD -> PyNames.STATICMETHOD
    }
  }

  override fun getContainingClass(): PyAstClass? =
    PsiTreeUtil.getParentOfType(this, StubBasedPsiElement::class.java) as? PyAstClass

  override fun acceptPyVisitor(pyVisitor: PyAstElementVisitor): Unit =
    pyVisitor.visitPyFunction(this)

  override fun getTypeComment(): PsiComment? {
    val inlineComment = PyUtilCore.getCommentOnHeaderLine(this)
    if (inlineComment != null && PyUtilCore.getTypeCommentValue(inlineComment.text) != null) {
      return inlineComment
    }

    val statements = statementList
    if (statements.statements.isNotEmpty()) {
      val comment = statements.firstChild as? PsiComment
      if (comment != null && PyUtilCore.getTypeCommentValue(comment.text) != null) {
        return comment
      }
    }
    return null
  }
}