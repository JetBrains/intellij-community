// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.ast.impl.PyPsiUtilsCore
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyElementType
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyExpressionCodeFragment
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyPassStatement
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PyStatementList
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyTargetExpression

object PyPsiUtils {
  private val LOG = Logger.getInstance(PyPsiUtils::class.java.name)

  /**
   * Finds the closest comma after the element skipping any whitespaces in-between.
   */
  @JvmStatic
  fun getPrevComma(element: PsiElement): PsiElement? {
    val prevNode = getPrevNonWhitespaceSibling(element)
    return if (prevNode != null && prevNode.node.elementType === PyTokenTypes.COMMA) prevNode else null
  }

  /**
   * Finds first non-whitespace sibling before given PSI element.
   */
  @JvmStatic
  fun getPrevNonWhitespaceSibling(element: PsiElement?): PsiElement? {
    return PsiTreeUtil.skipWhitespacesBackward(element)
  }

  /**
   * Finds first non-whitespace sibling before given AST node.
   */
  @JvmStatic
  fun getPrevNonWhitespaceSibling(node: ASTNode): ASTNode? {
    return skipSiblingsBackward(node, TokenSet.WHITE_SPACE)
  }

  /**
   * Finds first sibling that is neither comment, nor whitespace before given element.
   *
   * @param strict prohibit returning element itself
   */
  @JvmStatic
  fun getPrevNonCommentSibling(start: PsiElement?, strict: Boolean): PsiElement? {
    if (!strict && !(start is PsiWhiteSpace || start is PsiComment)) {
      return start
    }
    return PsiTreeUtil.skipWhitespacesAndCommentsBackward(start)
  }

  /**
   * Finds the closest comma after the element skipping any whitespaces in-between.
   */
  @JvmStatic
  fun getNextComma(element: PsiElement): PsiElement? {
    val nextNode = getNextNonWhitespaceSibling(element)
    return if (nextNode != null && nextNode.node.elementType === PyTokenTypes.COMMA) nextNode else null
  }

  /**
   * Finds first non-whitespace sibling after given PSI element.
   */
  @JvmStatic
  fun getNextNonWhitespaceSibling(element: PsiElement?): PsiElement? {
    return PsiTreeUtil.skipWhitespacesForward(element)
  }

  /**
   * Returns the first non-whitespace sibling preceding the given element but within its line boundaries.
   */
  @JvmStatic
  fun getPrevNonWhitespaceSiblingOnSameLine(element: PsiElement): PsiElement? {
    var cur = element.prevSibling
    while (cur != null) {
      if (cur !is PsiWhiteSpace) {
        return cur
      }
      else if (cur.textContains('\n')) {
        break
      }
      cur = cur.prevSibling
    }
    return null
  }

  /**
   * Finds first non-whitespace sibling after given AST node.
   */
  @JvmStatic
  fun getNextNonWhitespaceSibling(after: ASTNode): ASTNode? {
    return skipSiblingsForward(after, TokenSet.WHITE_SPACE)
  }

  /**
   * Finds first sibling that is neither comment, nor whitespace after given element.
   *
   * @param strict prohibit returning element itself
   */
  @JvmStatic
  fun getNextNonCommentSibling(start: PsiElement?, strict: Boolean): PsiElement? {
    return PyPsiUtilsCore.getNextNonCommentSibling(start, strict)
  }

  /**
   * Finds first token after given element that doesn't consist solely of spaces and is not empty (e.g. error marker).
   *
   * @param ignoreComments ignore commentaries as well
   */
  @JvmStatic
  fun getNextSignificantLeaf(element: PsiElement?, ignoreComments: Boolean): PsiElement? {
    var element = element
    while (element != null && element.text.isNullOrBlank() || ignoreComments && element is PsiComment) {
      element = PsiTreeUtil.nextLeaf(element)
    }
    return element
  }

  /**
   * Finds first token before given element that doesn't consist solely of spaces and is not empty (e.g. error marker).
   *
   * @param ignoreComments ignore commentaries as well
   */
  @JvmStatic
  fun getPrevSignificantLeaf(element: PsiElement?, ignoreComments: Boolean): PsiElement? {
    var element = element
    while (element != null && element.text.isNullOrBlank() || ignoreComments && element is PsiComment) {
      element = PsiTreeUtil.prevLeaf(element)
    }
    return element
  }

  /**
   * Finds the closest comma looking for the next comma first and then for the preceding one.
   */
  @JvmStatic
  fun getAdjacentComma(element: PsiElement): PsiElement? {
    val nextComma = getNextComma(element)
    return nextComma ?: getPrevComma(element)
  }

  /**
   * Works similarly to [PsiTreeUtil.skipSiblingsForward], but for AST nodes.
   */
  @JvmStatic
  fun skipSiblingsForward(node: ASTNode?, types: TokenSet): ASTNode? {
    if (node == null) {
      return null
    }
    var next = node.treeNext
    while (next != null) {
      if (!types.contains(next.elementType)) {
        return next
      }
      next = next.treeNext
    }
    return null
  }

  /**
   * Works similarly to [PsiTreeUtil.skipSiblingsBackward], but for AST nodes.
   */
  @JvmStatic
  fun skipSiblingsBackward(node: ASTNode?, types: TokenSet): ASTNode? {
    if (node == null) {
      return null
    }
    var prev = node.treePrev
    while (prev != null) {
      if (!types.contains(prev.elementType)) {
        return prev
      }
      prev = prev.treePrev
    }
    return null
  }

  /**
   * Returns first child psi element with specified element type or `null` if no such element exists.
   * Semantically it's the same as `getChildByFilter(element, TokenSet.create(type), 0)`.
   *
   * @param element tree parent node
   * @param type    element type expected
   * @return child element described
   */
  @JvmStatic
  fun getFirstChildOfType(element: PsiElement, type: PyElementType): PsiElement? {
    return PyPsiUtilsCore.getFirstChildOfType(element, type)
  }

  /**
   * Returns child element in the psi tree
   *
   * @param filter  Types of expected child
   * @param number  number
   * @param element tree parent node
   * @return PsiElement - child psiElement
   */
  @JvmStatic
  fun getChildByFilter(element: PsiElement, filter: TokenSet, number: Int): PsiElement? {
    return PyPsiUtilsCore.getChildByFilter(element, filter, number)
  }

  @JvmStatic
  fun removeElements(vararg elements: PsiElement) {
    val parentNode = elements[0].parent.node
    LOG.assertTrue(parentNode != null)
    for (element in elements) {
      parentNode.removeChild(element.node)
    }
  }

  @JvmStatic
  fun getStatement(element: PsiElement): PsiElement? {
    val compStatement = getStatementList(element)
    if (compStatement == null) {
      return null
    }
    return getParentRightBefore(element, compStatement)
  }

  @JvmStatic
  fun getStatementList(element: PsiElement?): PyElement? {
    return if (element is PyFile || element is PyStatementList)
      element
    else
      PsiTreeUtil.getParentOfType(element, PyFile::class.java, PyStatementList::class.java)
  }

  /**
   * Returns ancestor of the element that is also direct child of the given super parent.
   *
   * @param element     element to start search from
   * @param superParent direct parent of the desired ancestor
   * @return described element or `null` if it doesn't exist
   */
  @JvmStatic
  fun getParentRightBefore(element: PsiElement, superParent: PsiElement): PsiElement? {
    return PyPsiUtilsCore.getParentRightBefore(element, superParent)
  }

  @JvmStatic
  fun getElementIndentation(element: PsiElement): Int {
    val compStatement: PsiElement? = getStatementList(element)
    val statement = getParentRightBefore(element, compStatement!!)
    if (statement == null) {
      return 0
    }
    var sibling = statement.prevSibling
    if (sibling == null) {
      sibling = compStatement.prevSibling
    }
    val whitespace = if (sibling is PsiWhiteSpace) sibling.text else ""
    val i = whitespace.lastIndexOf("\n")
    return if (i != -1) whitespace.length - i - 1 else 0
  }

  @JvmStatic
  fun removeRedundantPass(statementList: PyStatementList) {
    val statements = statementList.statements
    if (statements.size > 1) {
      for (statement in statements) {
        if (statement is PyPassStatement) {
          statement.delete()
        }
      }
    }
  }

  @JvmStatic
  fun isMethodContext(element: PsiElement?): Boolean {
    val parent: PsiNamedElement? = PsiTreeUtil.getParentOfType(element, PyFile::class.java, PyFunction::class.java, PyClass::class.java)
    // In case if element is inside method which is inside class
    return parent is PyFunction &&
           PsiTreeUtil.getParentOfType(parent, PyFile::class.java, PyFunction::class.java, PyClass::class.java) is PyClass
  }


  @JvmStatic
  fun getRealContext(element: PsiElement): PsiElement {
    assertValid(element)
    val file = element.containingFile
    if (file is PyExpressionCodeFragment) {
      val context = file.realContext
      return context ?: element
    }
    else {
      return element
    }
  }

  /**
   * Removes comma closest to the given child node along with any whitespaces around. First following comma is checked and only
   * then, if it doesn't exists, preceding one.
   *
   * @param element parent node
   * @param child   child node comma should be adjacent to
   * @see .getAdjacentComma
   */
  @JvmStatic
  fun deleteAdjacentCommaWithWhitespaces(element: PsiElement, child: PsiElement) {
    val commaNode = getAdjacentComma(child)
    if (commaNode != null) {
      val nextNonWhitespace = getNextNonWhitespaceSibling(commaNode)
      val last = if (nextNonWhitespace == null) element.lastChild else nextNonWhitespace.prevSibling
      val prevNonWhitespace = getPrevNonWhitespaceSibling(commaNode)
      val first = if (prevNonWhitespace == null) element.firstChild else prevNonWhitespace.nextSibling
      element.deleteChildRange(first, last)
    }
  }

  /**
   * Returns comments preceding given elements as pair of the first and the last such comments. Comments should not be
   * separated by any empty line.
   *
   * @param element element comments should be adjacent to
   * @return described range or `null` if there are no such comments
   */
  @JvmStatic
  fun getPrecedingComments(element: PsiElement): List<PsiComment> {
    return getPrecedingComments(element, true)
  }

  @JvmStatic
  fun getPrecedingComments(element: PsiElement, stopAtBlankLine: Boolean): List<PsiComment> {
    return getPrecedingCommentsAndAnchor(element, stopAtBlankLine, true).first
  }

  private fun getPrecedingCommentsAndAnchor(
    element: PsiElement, stopAtBlankLine: Boolean,
    strict: Boolean,
  ): Pair<List<PsiComment>, PsiElement?> {
    val result = ArrayList<PsiComment>()
    var cursor = if (element is PsiComment && !strict) element else element.prevSibling
    while (true) {
      var newLinesCount = 0
      while (cursor is PsiWhiteSpace) {
        newLinesCount += StringUtil.getLineBreakCount(cursor.text)
        cursor = cursor.prevSibling
      }
      if ((stopAtBlankLine && newLinesCount > 1) || cursor !is PsiComment) {
        break
      }
      else {
        result.add(cursor)
      }
      cursor = cursor.prevSibling
    }
    result.reverse()
    return result to cursor
  }

  /**
   * Return blank-line-separated blocks of consecutive comments preceding the given element.
   *
   *
   * For instance, for the following fragment, it will return two blocks of one and two comments.
   *
   * <pre>`# comment # comment # comment def func():     pass `</pre>
   *
   *
   * Note that in the following case it will additionally return an empty list of comments as the last element
   * to distinguish between the cases when there is a blank line above the provided element and when there is not.
   *
   * <pre>`# comment def func():     pass `</pre>
   *
   */
  @JvmStatic
  fun getPrecedingCommentBlocks(element: PsiElement): List<List<PsiComment>> {
    val blocks: MutableList<List<PsiComment>> = ArrayList()
    var anchor: PsiElement? = element
    do {
      val blockAndAnchor = getPrecedingCommentsAndAnchor(anchor!!, true, false)
      anchor = blockAndAnchor.second
      val block = blockAndAnchor.first
      if (!block.isEmpty() || anchor is PsiComment) {
        blocks.add(block)
      }
    }
    while (anchor is PsiComment)
    blocks.reverse()
    return blocks
  }

  @JvmStatic
  fun findArgumentIndex(call: PyCallExpression, argument: PsiElement?): Int {
    val args = call.arguments
    for (i in args.indices) {
      var expression = args[i]
      if (expression is PyKeywordArgument) {
        expression = expression.valueExpression
      }
      expression = flattenParens(expression)
      if (expression === argument) {
        return i
      }
    }
    return -1
  }

  @JvmStatic
  fun getAttribute(file: PyFile, name: String): PyTargetExpression? {
    val attr = file.findTopLevelAttribute(name)
    if (attr == null) {
      for (element in file.fromImports) {
        val expression = element.importSource
        if (expression == null) continue
        val resolved = expression.reference.resolve()
        if (resolved is PyFile && resolved !== file) {
          return resolved.findTopLevelAttribute(name)
        }
      }
    }
    return attr
  }

  @JvmStatic
  fun getAttributeValuesFromFile(file: PyFile, name: String): MutableList<PyExpression?> {
    val result: MutableList<PyExpression?> = ArrayList()
    val attr = file.findTopLevelAttribute(name)
    if (attr != null) {
      sequenceToList(result, attr.findAssignedValue())
    }
    return result
  }

  @JvmStatic
  fun sequenceToList(result: MutableList<in PyExpression?>, value: PyExpression?) {
    var value = value
    value = flattenParens(value)
    if (value is PySequenceExpression) {
      ContainerUtil.addAll(result, *value.elements)
    }
    else {
      result.add(value)
    }
  }

  @JvmStatic
  fun getStringValues(elements: Array<PyExpression?>): MutableList<String?> {
    val results: MutableList<String?> = ArrayList()
    for (element in elements) {
      if (element is PyStringLiteralExpression) {
        results.add(element.stringValue)
      }
    }
    return results
  }

  @JvmStatic
  fun flattenParens(expr: PyExpression?): PyExpression? {
    return PyPsiUtilsCore.flattenParens(expr) as PyExpression?
  }

  @JvmStatic
  fun strValue(expression: PyExpression?): String? {
    return PyPsiUtilsCore.strValue(expression)
  }

  @JvmStatic
  fun isBefore(element: PsiElement, element2: PsiElement): Boolean {
    // TODO: From RubyPsiUtil, should be moved to PsiTreeUtil
    return element.textOffset <= element2.textOffset
  }

  @JvmStatic
  fun asQualifiedName(expr: PyExpression?): QualifiedName? {
    return PyPsiUtilsCore.asQualifiedName(expr)
  }

  @JvmStatic
  fun getFirstQualifier(expr: PyQualifiedExpression): PyExpression {
    val qualifier = expr.qualifier
    if (qualifier is PyQualifiedExpression) {
      return getFirstQualifier(qualifier)
    }
    return expr
  }

  @JvmStatic
  fun toPath(expr: PyQualifiedExpression?): String {
    if (expr != null) {
      val qName = expr.asQualifiedName()
      if (qName != null) {
        return qName.toString()
      }
      val name = expr.name
      if (name != null) {
        return name
      }
    }
    return ""
  }

  @JvmStatic
  fun asQualifiedName(expr: PyQualifiedExpression): QualifiedName? {
    return PyPsiUtilsCore.asQualifiedName(expr)
  }

  /**
   * Wrapper for [PsiUtilCore.ensureValid] that skips nulls
   */
  @JvmStatic
  fun assertValid(element: PsiElement?) {
    PyPsiUtilsCore.assertValid(element)
  }

  @JvmStatic
  fun assertValid(module: Module) {
    LOG.assertTrue(!module.isDisposed, String.format("Module %s is disposed", module))
  }

  @JvmStatic
  fun getFileSystemItem(element: PsiElement): PsiFileSystemItem? {
    if (element is PsiFileSystemItem) {
      return element
    }
    return element.containingFile
  }

  @JvmStatic
  fun getContainingFilePath(element: PsiElement): String? {
    val file: VirtualFile?
    if (element is PsiFileSystemItem) {
      file = element.virtualFile
    }
    else {
      file = element.containingFile.virtualFile
    }
    if (file != null) {
      return FileUtil.toSystemDependentName(file.path)
    }
    return null
  }

  /**
   * Checks if specified file contains passed source in top-level import in stub-safe way.
   * Does not process scopes inside the file.
   *
   * @param file   file whose imports should be visited
   * @param source qualified name separated by dots that is looking for in imports
   * @return true if specified file contains passed source in top-level import.
   */
  @JvmStatic
  fun containsImport(file: PyFile, source: String): Boolean {
    val sourceQName = QualifiedName.fromDottedString(source)

    return (
      file.fromImports.map { it.importSourceQName } +
      file.importTargets.map { it.importedQName }
           )
      .filterNotNull()
      .any { it.matchesPrefix(sourceQName) }
  }

  /**
   * Returns text of the given PSI element. Unlike obvious [PsiElement.getText] this method unescapes text of the element if latter
   * belongs to injected code fragment using [InjectedLanguageManager.getUnescapedText].
   *
   * @param element PSI element which text is needed
   * @return text of the element with any host escaping removed
   */
  @JvmStatic
  fun getElementTextWithoutHostEscaping(element: PsiElement): String {
    val manager = InjectedLanguageManager.getInstance(element.project)
    if (manager.isInjectedFragment(element.containingFile)) {
      return manager.getUnescapedText(element)
    }
    else {
      return element.text
    }
  }

  @JvmStatic
  fun getStringValue(o: PsiElement?): String? {
    if (o == null) {
      return null
    }
    if (o is PyStringLiteralExpression) {
      return o.stringValue
    }
    else {
      return o.text
    }
  }

  @JvmStatic
  fun getStringValueTextRange(element: PsiElement): TextRange {
    if (element is PyStringLiteralExpression) {
      return element.stringValueTextRanges[0]
    }
    else {
      return TextRange(0, element.textLength)
    }
  }

  @JvmStatic
  fun findSameLineComment(elem: PsiElement): PsiComment? {
    // If `elem` is a compound multi-line element, stick to its first line nonetheless
    var next: PsiElement? = PsiTreeUtil.getDeepestFirst(elem)
    do {
      if (next is PsiComment) {
        return next
      }
      if (next !== elem && next!!.textContains('\n')) {
        break
      }
      next = PsiTreeUtil.nextLeaf(next)
    }
    while (next != null)
    return null
  }
}
