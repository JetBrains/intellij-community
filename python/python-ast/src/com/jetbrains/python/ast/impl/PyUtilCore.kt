package com.jetbrains.python.ast.impl

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.ast.PyAstAssignmentStatement
import com.jetbrains.python.ast.PyAstBreakStatement
import com.jetbrains.python.ast.PyAstContinueStatement
import com.jetbrains.python.ast.PyAstExpression
import com.jetbrains.python.ast.PyAstExpressionStatement
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.ast.PyAstListLiteralExpression
import com.jetbrains.python.ast.PyAstLoopStatement
import com.jetbrains.python.ast.PyAstParenthesizedExpression
import com.jetbrains.python.ast.PyAstSequenceExpression
import com.jetbrains.python.ast.PyAstStarExpression
import com.jetbrains.python.ast.PyAstStatement
import com.jetbrains.python.ast.PyAstStatementListContainer
import com.jetbrains.python.ast.PyAstStatementWithElse
import com.jetbrains.python.ast.PyAstStringLiteralExpression
import com.jetbrains.python.ast.PyAstTupleExpression
import com.jetbrains.python.ast.controlFlow.AstScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtilCore.getScopeOwner
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import java.util.regex.Pattern

/**
 * Assorted utility methods for Python code insight.
 * 
 * These methods don't depend on the Python runtime.
 * 
 * @see PyPsiUtilsCore for utilities used in Python PSI API
 */
@ApiStatus.Experimental
object PyUtilCore {
  private val TYPE_COMMENT_PATTERN: Pattern = Pattern.compile("# *type: *([^#]+) *(#.*)?")

  /**
   * @see PyUtil.flattenedParensAndTuples
   */
  private fun unfoldParentheses(
    targets: Array<out PyAstExpression?>, receiver: MutableList<PyAstExpression?>,
    unfoldListLiterals: Boolean, unfoldStarExpressions: Boolean,
  ): MutableList<PyAstExpression?> {
    // NOTE: this proliferation of instanceofs is not very beautiful. Maybe rewrite using a visitor.
    for (exp in targets) {
      if (exp is PyAstParenthesizedExpression) {
        unfoldParentheses(
          arrayOf(exp.containedExpression),
          receiver,
          unfoldListLiterals,
          unfoldStarExpressions
        )
      }
      else if (exp is PyAstTupleExpression<*>) {
        unfoldParentheses(exp.elements, receiver, unfoldListLiterals, unfoldStarExpressions)
      }
      else if (exp is PyAstListLiteralExpression && unfoldListLiterals) {
        unfoldParentheses(exp.elements, receiver, true, unfoldStarExpressions)
      }
      else if (exp is PyAstStarExpression && unfoldStarExpressions) {
        unfoldParentheses(arrayOf(exp.expression), receiver, unfoldListLiterals, true)
      }
      else if (exp != null) {
        receiver.add(exp)
      }
    }
    return receiver
  }

  /**
   * Flattens the representation of every element in targets, and puts all results together.
   * Elements of every tuple nested in target item are brought to the top level: (a, (b, (c, d))) -> (a, b, c, d)
   * Typical usage: `flattenedParensAndTuples(some_tuple.getExpressions())`.
   *
   * @param targets target elements.
   * @return the list of flattened expressions.
   */
  @JvmStatic
  fun flattenedParensAndTuples(vararg targets: PyAstExpression?): MutableList<PyAstExpression?> {
    return unfoldParentheses(targets, ArrayList(targets.size), false, false)
  }

  @JvmStatic
  fun flattenedParensAndLists(vararg targets: PyAstExpression?): MutableList<PyAstExpression?> {
    return unfoldParentheses(targets, ArrayList(targets.size), true, true)
  }

  @JvmStatic
  fun flattenedParensAndStars(vararg targets: PyAstExpression?): MutableList<PyAstExpression?> {
    return unfoldParentheses(targets, ArrayList(targets.size), false, true)
  }

  @JvmStatic
  fun onSameLine(e1: PsiElement, e2: PsiElement): Boolean {
    val firstFile = e1.containingFile
    val secondFile = e2.containingFile
    if (firstFile == null || secondFile == null) return false
    val document = firstFile.fileDocument
    if (document !== secondFile.fileDocument) return false
    return document.getLineNumber(e1.textOffset) == document.getLineNumber(e2.textOffset)
  }

  @JvmStatic
  fun isTopLevel(element: PsiElement): Boolean {
    return getScopeOwner(element) is PsiFile
  }

  /**
   * Retrieves the document from [PsiDocumentManager] using the anchor PSI element and, if it's not null,
   * passes it to the consumer function.
   *
   *
   * The document is first released from pending PSI operations and then committed after the function has been applied
   * in a `try/finally` block, so that subsequent operations on PSI could be performed.
   *
   * @see PsiDocumentManager.doPostponedOperationsAndUnblockDocument
   * @see PsiDocumentManager.commitDocument
   * @see .updateDocumentUnblockedAndCommitted
   */
  @JvmStatic
  fun updateDocumentUnblockedAndCommitted(anchor: PsiElement, consumer: java.util.function.Consumer<in Document>) {
    updateDocumentUnblockedAndCommitted<Any?>(anchor) { consumer.accept(it) }
  }

  @JvmStatic
  fun <T> updateDocumentUnblockedAndCommitted(anchor: PsiElement, func: java.util.function.Function<in Document, out T?>): T? {
    val manager = PsiDocumentManager.getInstance(anchor.project)
    // manager.getDocument(anchor.getContainingFile()) doesn't work with intention preview
    val document = anchor.containingFile.viewProvider.document
    if (document != null) {
      manager.doPostponedOperationsAndUnblockDocument(document)
      try {
        return func.apply(document)
      }
      finally {
        manager.commitDocument(document)
      }
    }
    return null
  }

  @JvmStatic
  fun isSpecialName(name: String): Boolean {
    return name.length > 4 && name.startsWith("__") && name.endsWith("__")
  }

  @JvmStatic
  fun getCorrespondingLoop(breakOrContinue: PsiElement): PyAstLoopStatement? {
    return if (breakOrContinue is PyAstContinueStatement || breakOrContinue is PyAstBreakStatement)
      getCorrespondingLoopImpl(breakOrContinue)
    else
      null
  }

  private fun getCorrespondingLoopImpl(element: PsiElement): PyAstLoopStatement? {
    val loop =
      PsiTreeUtil.getParentOfType(element, PyAstLoopStatement::class.java, true, AstScopeOwner::class.java)

    if (loop is PyAstStatementWithElse && PsiTreeUtil.isAncestor((loop as PyAstStatementWithElse).elsePart, element, true)) {
      return getCorrespondingLoopImpl(loop)
    }

    return loop
  }

  /**
   * @return true if passed `element` is a method (this means a function inside a class) named `__new__`.
   * @see PyUtil.isInitMethod
   * @see PyUtil.isInitOrNewMethod
   * @see PyUtil.turnConstructorIntoClass
   */
  @JvmStatic
  @Contract("null -> false")
  fun isNewMethod(element: PsiElement?): Boolean {
    val function = element as? PyAstFunction
    return function != null && PyNames.NEW == function.name && function.containingClass != null
  }

  /**
   * @return true if passed `element` is a method (this means a function inside a class) named `__init__` or `__new__`.
   * @see PyUtil.isInitMethod
   * @see PyUtil.isNewMethod
   * @see PyUtil.turnConstructorIntoClass
   */
  @JvmStatic
  @Contract("null -> false")
  fun isInitOrNewMethod(element: PsiElement?): Boolean {
    val function = element as? PyAstFunction
    if (function == null) return false

    val name = function.name
    return (PyNames.INIT == name ||
            PyNames.NEW == name) && function.containingClass != null
  }

  /**
   * @return true if passed `element` is a method (this means a function inside a class) named `__init__`,
   * `__init_subclass__`, or `__new__`.
   * @see PyUtil.isInitMethod
   * @see PyUtil.isNewMethod
   * @see PyUtil.turnConstructorIntoClass
   */
  @JvmStatic
  @Contract("null -> false")
  fun isConstructorLikeMethod(element: PsiElement?): Boolean {
    val function = element as? PyAstFunction
    if (function == null) return false

    val name = function.name
    return (PyNames.INIT_SUBCLASS == name ||
            PyNames.INIT == name ||
            PyNames.NEW == name) && function.containingClass != null
  }

  @JvmStatic
  fun isStringLiteral(stmt: PyAstStatement?): Boolean {
    if (stmt is PyAstExpressionStatement) {
      val expr = stmt.expression
      if (expr is PyAstStringLiteralExpression) {
        return true
      }
    }
    return false
  }

  /**
   * Counts initial underscores of an identifier.
   *
   * @param name identifier
   * @return 0 if null or no initial underscores found, 1 if there's only one underscore, 2 if there's two or more initial underscores.
   */
  @JvmStatic
  fun getInitialUnderscores(name: String?): Int {
    return if (name == null) 0 else if (name.startsWith("__")) 2 else if (name.startsWith(PyNames.UNDERSCORE)) 1 else 0
  }

  @JvmStatic
  fun strListValue(value: PyAstExpression?): MutableList<String>? {
    var value = value
    while (value is PyAstParenthesizedExpression) {
      value = value.containedExpression
    }
    if (value is PyAstSequenceExpression) {
      val elements = value.elements
      val result: MutableList<String> = ArrayList(elements.size)
      for (element in elements) {
        if (element !is PyAstStringLiteralExpression) {
          return null
        }
        result.add(element.stringValue)
      }
      return result
    }
    return null
  }

  @JvmStatic
  fun isAssignmentToModuleLevelDunderName(element: PsiElement?): Boolean {
    if (element is PyAstAssignmentStatement && isTopLevel(element)) {
      val targets = element.targets
      if (targets.size == 1) {
        val name = targets[0]!!.name
        return name != null && isSpecialName(name)
      }
    }
    return false
  }

  /**
   * Returns the line comment that immediately precedes statement list of the given compound statement. Python parser ensures
   * that it follows the statement header, i.e. it's directly after the colon, not on its own line.
   */
  @JvmStatic
  fun getCommentOnHeaderLine(container: PyAstStatementListContainer): PsiComment? {
    return getHeaderEndAnchor(container) as? PsiComment
  }

  @JvmStatic
  fun getHeaderEndAnchor(container: PyAstStatementListContainer): PsiElement {
    val statementList = container.statementList
    return PsiTreeUtil.skipWhitespacesBackward(statementList)!!
  }

  /**
   * Checks that text of a comment starts with "# type:" prefix and returns trimmed type hint after it.
   * The trailing part is supposed to contain type annotation in PEP 484 compatible format and an optional
   * plain text comment separated from it with another "#".
   *
   *
   * For instance, for `# type: List[int]  # comment` it returns `List[int]`.
   *
   *
   * This method cannot return an empty string.
   *
   * @see .getTypeCommentValueRange
   */
  @JvmStatic
  fun getTypeCommentValue(text: String): String? {
    val m = TYPE_COMMENT_PATTERN.matcher(text)
    if (m.matches()) {
      return StringUtil.nullize(m.group(1).trim { it <= ' ' })
    }
    return null
  }

  /**
   * Returns the corresponding text range for a type hint as returned by [.getTypeCommentValue].
   *
   * @see .getTypeCommentValue
   */
  @JvmStatic
  fun getTypeCommentValueRange(text: String): TextRange? {
    val m = TYPE_COMMENT_PATTERN.matcher(text)
    if (m.matches()) {
      val hint = getTypeCommentValue(text)
      if (hint != null) {
        return TextRange.from(m.start(1), hint.length)
      }
    }
    return null
  }

  @JvmStatic
  fun findPrevAtOffset(psiFile: PsiFile, caretOffset: Int, vararg toSkip: Class<out PsiElement?>): PsiElement? {
    var caretOffset = caretOffset
    var element: PsiElement?
    if (caretOffset < 0) {
      return null
    }
    var lineStartOffset = 0
    val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)
    if (document != null) {
      val lineNumber = document.getLineNumber(caretOffset)
      lineStartOffset = document.getLineStartOffset(lineNumber)
    }
    do {
      caretOffset--
      element = psiFile.findElementAt(caretOffset)
    }
    while (caretOffset >= lineStartOffset && PsiTreeUtil.instanceOf(element, *toSkip))
    return if (PsiTreeUtil.instanceOf(element, *toSkip)) null else element
  }

  @JvmStatic
  fun findNonWhitespaceAtOffset(psiFile: PsiFile, caretOffset: Int): PsiElement? {
    var element = findNextAtOffset(psiFile, caretOffset, PsiWhiteSpace::class.java)
    if (element == null) {
      element = findPrevAtOffset(psiFile, caretOffset - 1, PsiWhiteSpace::class.java)
    }
    return element
  }

  @JvmStatic
  fun findNextAtOffset(psiFile: PsiFile, caretOffset: Int, vararg toSkip: Class<out PsiElement?>): PsiElement? {
    var caretOffset = caretOffset
    var element = psiFile.findElementAt(caretOffset)
    if (element == null) {
      return null
    }

    val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)
    var lineEndOffset = 0
    if (document != null) {
      val lineNumber = document.getLineNumber(caretOffset)
      lineEndOffset = document.getLineEndOffset(lineNumber)
    }
    while (caretOffset < lineEndOffset && PsiTreeUtil.instanceOf(element, *toSkip)) {
      caretOffset++
      element = psiFile.findElementAt(caretOffset)
    }
    return if (PsiTreeUtil.instanceOf(element, *toSkip)) null else element
  }
}
