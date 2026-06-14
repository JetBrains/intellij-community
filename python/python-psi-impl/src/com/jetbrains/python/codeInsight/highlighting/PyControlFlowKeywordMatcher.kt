// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.highlighting

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.containers.addIfNotNull
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyBreakStatement
import com.jetbrains.python.psi.PyContinueStatement
import com.jetbrains.python.psi.PyForStatement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyLambdaExpression
import com.jetbrains.python.psi.PyLoopStatement
import com.jetbrains.python.psi.PyMatchStatement
import com.jetbrains.python.psi.PyRaiseStatement
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import com.jetbrains.python.psi.PyReturnStatement
import com.jetbrains.python.psi.PyStatementList
import com.jetbrains.python.psi.PyStatementPart
import com.jetbrains.python.psi.PyWhileStatement
import com.jetbrains.python.psi.PyYieldExpression
import org.jetbrains.annotations.ApiStatus

/**
 * Matches a control-flow keyword with the other related keywords of the same construct so that they
 * can be highlighted together, previewed when scrolled off screen and navigated between. Two kinds
 * of relations are recognized:
 *
 *  * parts of a multipart compound statement &mdash; `if`/`elif`/`else`, `for`/`else`,
 *    `while`/`else`, `try`/`except`/`else`/`finally`, `match`/`case`;
 *  * a function header and the exit keywords inside its body &mdash; `def` with its `return`,
 *    `yield` and `raise` keywords;
 *  * a loop header and the jump keywords inside its body &mdash; `for`/`while` with the `break` and
 *    `continue` keywords bound to it.
 *
 * Shared by `PyControlFlowKeywordBraceHighlighter` (off-screen preview of the construct header) and
 * `PyControlFlowKeywordCodeBlockSupportHandler` (matching-keyword highlighting and "Move Caret to
 * Matching Brace" navigation between the parts of a compound statement).
 */
@ApiStatus.Internal
object PyControlFlowKeywordMatcher {
  /** Leading keyword tokens of the multipart compound statements we navigate between. */
  private val COMPOUND_PART_KEYWORDS: TokenSet = TokenSet.create(
    PyTokenTypes.IF_KEYWORD,
    PyTokenTypes.ELIF_KEYWORD,
    PyTokenTypes.ELSE_KEYWORD,
    PyTokenTypes.FOR_KEYWORD,
    PyTokenTypes.WHILE_KEYWORD,
    PyTokenTypes.TRY_KEYWORD,
    PyTokenTypes.EXCEPT_KEYWORD,
    PyTokenTypes.FINALLY_KEYWORD,
    PyTokenTypes.MATCH_KEYWORD,
    PyTokenTypes.CASE_KEYWORD,
  )

  /** The function header keyword and the exit keywords matched with it. */
  private val FUNCTION_EXIT_KEYWORDS: TokenSet = TokenSet.create(
    PyTokenTypes.DEF_KEYWORD,
    PyTokenTypes.RETURN_KEYWORD,
    PyTokenTypes.YIELD_KEYWORD,
    PyTokenTypes.RAISE_KEYWORD,
  )

  /** The `break`/`continue` keywords matched with the header of the loop they belong to. */
  private val LOOP_EXIT_KEYWORDS: TokenSet = TokenSet.create(
    PyTokenTypes.BREAK_KEYWORD,
    PyTokenTypes.CONTINUE_KEYWORD,
  )

  /**
   * @param keywords related keyword elements of the construct, in document order (the construct
   *   header &mdash; `if`, `try`, `def`, &hellip; &mdash; comes first)
   * @param caretIndex index in [keywords] of the keyword the caret is on
   */
  data class KeywordContext(@JvmField val keywords: List<PsiElement>, @JvmField val caretIndex: Int)

  /**
   * Returns the keyword context if [offset] (or the position just before it) is on a keyword that
   * participates in one of the recognized relations, or `null` otherwise. A ternary `if`/`else`, a
   * comprehension `for`/`if`, a `return`/`yield` outside a function and the like are intentionally
   * ignored.
   */
  fun findKeywordContext(file: PsiFile, offset: Int): KeywordContext? {
    return contextAtOffset(file, offset) ?: if (offset > 0) contextAtOffset(file, offset - 1) else null
  }

  private fun contextAtOffset(file: PsiFile, offset: Int): KeywordContext? {
    val leaf = file.findElementAt(offset) ?: return null
    val keywords = keywordGroup(leaf) ?: return null
    val caretIndex = keywords.indexOfFirst { it.textRange == leaf.textRange }
    if (caretIndex < 0) return null
    return KeywordContext(keywords, caretIndex)
  }

  private fun keywordGroup(leaf: PsiElement): List<PsiElement>? {
    val type = leaf.elementType
    return when (type) {
      in COMPOUND_PART_KEYWORDS -> compoundStatementKeywords(leaf)
      in FUNCTION_EXIT_KEYWORDS -> functionExitKeywords(leaf)
      in LOOP_EXIT_KEYWORDS -> loopExitKeywords(leaf)
      else -> null
    }
  }

  // region multipart compound statements

  /**
   * Text ranges of the keywords of the multipart compound statement that [keyword] opens a part of
   * (`if`/`elif`/`else`, `for`/`else`, `while`/`else`, `try`/`except`/`else`/`finally`,
   * `match`/`case`), in document order, or `null` if [keyword] is not such a keyword. Powers the
   * platform `codeBlockSupportHandler` (matching-keyword highlighting and "Move Caret to Matching
   * Brace").
   */
  fun compoundStatementKeywordRanges(keyword: PsiElement): List<TextRange>? {
    if (keyword.elementType !in COMPOUND_PART_KEYWORDS) return null
    return compoundStatementKeywords(keyword)?.map { it.textRange }
  }

  /** Text range of the multipart compound statement [keyword] belongs to, or `null`. */
  fun compoundStatementRange(keyword: PsiElement): TextRange? {
    if (keyword.elementType !in COMPOUND_PART_KEYWORDS) return null
    return enclosingCompoundStatement(keyword)?.textRange
  }

  private fun enclosingCompoundStatement(keyword: PsiElement): PsiElement? =
    when (val parent = keyword.parent) {
      // `if`/`elif`/`else`/`for`/`while`/`try`/`except`/`finally`/`case` keywords open a statement part
      is PyStatementPart -> parent.parent
      // the bare `match` keyword is a direct token child of the match statement
      is PyMatchStatement -> parent
      else -> null
    }

  private fun compoundStatementKeywords(keyword: PsiElement): List<PsiElement>? {
    val compoundStatement = enclosingCompoundStatement(keyword) ?: return null
    val result = mutableListOf<PsiElement>()
    var child = compoundStatement.firstChild
    while (child != null) {
      when {
        // direct keyword token child, e.g. the `match` keyword of a match statement
        child.elementType in COMPOUND_PART_KEYWORDS -> result.add(child)
        child is PyStatementPart -> result.addIfNotNull(leadingCompoundKeyword(child))
      }
      child = child.nextSibling
    }
    return result
  }

  // endregion

  // region function header and its exit statements

  private fun functionExitKeywords(keyword: PsiElement): List<PsiElement>? {
    val function = if (keyword.elementType == PyTokenTypes.DEF_KEYWORD) {
      keyword.parent as? PyFunction
    }
    else {
      // a `return`/`yield`/`raise` keyword belongs to its nearest enclosing function
      PsiTreeUtil.getParentOfType(keyword, PyFunction::class.java)
    } ?: return null

    val def = keywordChild(function, PyTokenTypes.DEF_KEYWORD) ?: return null
    val result = mutableListOf(def)
    function.statementList.accept(object : PyRecursiveElementVisitor() {
      // do not descend into nested functions/lambdas: their exits belong to their own `def`
      override fun visitPyFunction(node: PyFunction) {}
      override fun visitPyLambdaExpression(node: PyLambdaExpression) {}

      override fun visitPyReturnStatement(node: PyReturnStatement) {
        result.addIfNotNull(keywordChild(node, PyTokenTypes.RETURN_KEYWORD))
        super.visitPyReturnStatement(node)
      }

      override fun visitPyYieldExpression(node: PyYieldExpression) {
        result.addIfNotNull(keywordChild(node, PyTokenTypes.YIELD_KEYWORD))
        super.visitPyYieldExpression(node)
      }

      override fun visitPyRaiseStatement(node: PyRaiseStatement) {
        result.addIfNotNull(keywordChild(node, PyTokenTypes.RAISE_KEYWORD))
        super.visitPyRaiseStatement(node)
      }
    })
    return result
  }

  // endregion

  // region loop header and its break/continue statements

  private fun loopExitKeywords(keyword: PsiElement): List<PsiElement>? {
    val loop = when (val statement = keyword.parent) {
      is PyBreakStatement -> statement.loopStatement
      is PyContinueStatement -> statement.loopStatement
      else -> null
    } ?: return null

    val header = loopHeaderKeyword(loop) ?: return null
    val body = loopBody(loop) ?: return null
    val result = mutableListOf(header)
    body.accept(object : PyRecursiveElementVisitor() {
      // a `break`/`continue` binds to its nearest enclosing loop; stay out of nested loops and functions
      override fun visitPyForStatement(node: PyForStatement) {}
      override fun visitPyWhileStatement(node: PyWhileStatement) {}
      override fun visitPyFunction(node: PyFunction) {}
      override fun visitPyLambdaExpression(node: PyLambdaExpression) {}

      override fun visitPyBreakStatement(node: PyBreakStatement) {
        keywordChild(node, PyTokenTypes.BREAK_KEYWORD)?.let { result.add(it) }
      }

      override fun visitPyContinueStatement(node: PyContinueStatement) {
        keywordChild(node, PyTokenTypes.CONTINUE_KEYWORD)?.let { result.add(it) }
      }
    })
    return result
  }

  private fun loopHeaderKeyword(loop: PyLoopStatement): PsiElement? {
    return when (loop) {
      is PyForStatement -> keywordChild(loop.forPart, PyTokenTypes.FOR_KEYWORD)
      is PyWhileStatement -> keywordChild(loop.whilePart, PyTokenTypes.WHILE_KEYWORD)
      else -> null
    }
  }

  private fun loopBody(loop: PyLoopStatement): PyStatementList? {
    return when (loop) {
      is PyForStatement -> loop.forPart.statementList
      is PyWhileStatement -> loop.whilePart.statementList
      else -> null
    }
  }

  // endregion

  private fun leadingCompoundKeyword(part: PyStatementPart): PsiElement? {
    var child = part.firstChild
    while (child != null) {
      if (child.elementType in COMPOUND_PART_KEYWORDS) return child
      child = child.nextSibling
    }
    return null
  }

  private fun keywordChild(element: PsiElement, tokenType: IElementType): PsiElement? {
    return element.node.findChildByType(tokenType)?.psi
  }
}
