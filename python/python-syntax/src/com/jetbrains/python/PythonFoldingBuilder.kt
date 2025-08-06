// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.codeInsight.folding.CodeFoldingSettings
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.LineTokenizer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.jetbrains.python.ast.*
import com.jetbrains.python.psi.PyStringLiteralCoreUtil
import kotlin.math.max


open class PythonFoldingBuilder : CustomFoldingBuilder(), DumbAware {
  override fun buildLanguageFoldRegions(
    descriptors: MutableList<FoldingDescriptor?>,
    root: PsiElement,
    document: Document,
    quick: Boolean,
  ) {
    appendDescriptors(root.node, descriptors)
  }

  override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String {
    if (isImport(node)) {
      return "import ..."
    }
    if (node.elementType === PyElementTypes.STRING_LITERAL_EXPRESSION) {
      val stringLiteralExpression = node.psi as PyAstStringLiteralExpression
      val prefix = stringLiteralExpression.stringElements[0].prefix
      if (stringLiteralExpression.isDocString()) {
        val stringValue = stringLiteralExpression.stringValue.trim { it <= ' ' }
        val lines = LineTokenizer.tokenize(stringValue, true)
        if (lines.size > 2 && lines[1].trim { it <= ' ' }.isEmpty()) {
          return prefix + "\"\"\"" + lines[0].trim { it <= ' ' } + "...\"\"\""
        }
        return "$prefix\"\"\"...\"\"\""
      }
      else {
        return prefix + getLanguagePlaceholderForString(stringLiteralExpression)
      }
    }
    return "..."
  }

  override fun isRegionCollapsedByDefault(node: ASTNode): Boolean {
    if (isImport(node)) {
      return CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS
    }
    val elementType = node.elementType
    if (elementType === PyElementTypes.STRING_LITERAL_EXPRESSION) {
      val docStringOwnerType = getDocStringOwnerType(node)
      if (isFunction(docStringOwnerType) && CodeFoldingSettings.getInstance().COLLAPSE_METHODS) {
        // method will be collapsed, no need to also collapse docstring
        return false
      }
      if (docStringOwnerType != null) {
        return CodeFoldingSettings.getInstance().COLLAPSE_DOC_COMMENTS
      }
      return PythonFoldingSettings.getInstance().isCollapseLongStrings
    }
    if (elementType === PyTokenTypes.END_OF_LINE_COMMENT) {
      return PythonFoldingSettings.getInstance().isCollapseSequentialComments
    }
    if (elementType === PyElementTypes.ANNOTATION) {
      return PythonFoldingSettings.getInstance().isCollapseTypeAnnotations
    }
    if (elementType === PyElementTypes.STATEMENT_LIST && isFunction(node.treeParent.elementType)) {
      return CodeFoldingSettings.getInstance().COLLAPSE_METHODS
    }
    if (elementType in FOLDABLE_COLLECTIONS_LITERALS) {
      return PythonFoldingSettings.getInstance().isCollapseLongCollections
    }
    if (isLanguageSpecificFoldableBlock(elementType)) {
      return CodeFoldingSettings.getInstance().COLLAPSE_METHODS
    }
    return false
  }

  override fun keepExpandedOnFirstCollapseAll(node: ASTNode): Boolean {
    val elementType = node.getElementType()
    if (elementType === PyElementTypes.ANNOTATION) {
      return !PythonFoldingSettings.getInstance().isCollapseTypeAnnotations
    }
    return false
  }

  override fun isCustomFoldingCandidate(node: ASTNode): Boolean {
    return node.elementType === PyTokenTypes.END_OF_LINE_COMMENT
  }

  override fun isCustomFoldingRoot(node: ASTNode): Boolean {
    return node.psi is PyAstFile || node.elementType === PyElementTypes.STATEMENT_LIST
  }

  protected open fun isLanguageSpecificFoldableBlock(elementType: IElementType): Boolean {
    return false
  }

  private fun appendDescriptors(node: ASTNode, descriptors: MutableList<FoldingDescriptor?>) {
    val elementType = node.elementType
    if (node.psi is PyAstFile) {
      val imports = (node.psi as PyAstFile).importBlock
      if (imports.size > 1) {
        val firstImport: PyAstImportStatementBase = imports[0]
        val lastImport: PyAstImportStatementBase = imports[imports.size - 1]
        descriptors.add(FoldingDescriptor(firstImport, TextRange(firstImport.textRange.startOffset,
                                                                 lastImport.textRange.endOffset)))
      }
    }
    else if (elementType === PyElementTypes.MATCH_STATEMENT) {
      foldMatchStatement(node, descriptors)
    }
    else if (elementType === PyElementTypes.STATEMENT_LIST) {
      foldStatementList(node, descriptors)
    }
    else if (elementType === PyElementTypes.STRING_LITERAL_EXPRESSION) {
      foldLongStrings(node, descriptors)
    }
    else if (elementType in FOLDABLE_COLLECTIONS_LITERALS) {
      foldCollectionLiteral(node, descriptors)
    }
    else if (elementType === PyTokenTypes.END_OF_LINE_COMMENT) {
      foldSequentialComments(node, descriptors)
    }
    else if (elementType === PyElementTypes.ANNOTATION) {
      val annotation = node.psi
      if (annotation is PyAstAnnotation && annotation.value != null) {
        descriptors.add(FoldingDescriptor(node, annotation.value!!.textRange,
                                          FoldingGroup.newGroup(PYTHON_TYPE_ANNOTATION_GROUP_NAME)))
      }
    }
    else if (isLanguageSpecificFoldableBlock(elementType)) {
      val nodeRange = node.textRange
      if (!nodeRange.isEmpty) {
        val colon = node.findChildByType(PyTokenTypes.COLON)
        foldSegment(node, descriptors, nodeRange, colon)
      }
    }
    var child = node.firstChildNode
    while (child != null) {
      appendDescriptors(child, descriptors)
      child = child.treeNext
    }
  }

  private fun foldSequentialComments(node: ASTNode, descriptors: MutableList<FoldingDescriptor?>) {
    //do not start folded comments from custom region
    if (isCustomRegionElement(node.psi)) {
      return
    }
    //need to skip previous comments in sequence
    var curNode = node.treePrev
    while (curNode != null) {
      if (curNode.elementType === PyTokenTypes.END_OF_LINE_COMMENT) {
        if (isCustomRegionElement(curNode.psi)) {
          break
        }
        return
      }
      curNode = if (curNode.psi is PsiWhiteSpace) curNode.treePrev else null
    }

    //fold sequence comments in one block
    curNode = node.treeNext
    var lastCommentNode: ASTNode? = node
    while (curNode != null) {
      if (curNode.elementType === PyTokenTypes.END_OF_LINE_COMMENT) {
        //do not end folded comments with custom region
        if (isCustomRegionElement(curNode.psi)) {
          break
        }
        lastCommentNode = curNode
        curNode = curNode.treeNext
        continue
      }
      curNode = if (curNode.psi is PsiWhiteSpace) curNode.treeNext else null
    }

    if (lastCommentNode !== node) {
      descriptors.add(
        FoldingDescriptor(node, TextRange.create(node.startOffset, lastCommentNode!!.textRange.endOffset)))
    }
  }

  private fun foldCollectionLiteral(node: ASTNode, descriptors: MutableList<FoldingDescriptor?>) {
    if (StringUtil.countNewLines(node.chars) > 0) {
      val range = node.textRange
      val delta = if (node.elementType === PyElementTypes.TUPLE_EXPRESSION) 0 else 1
      descriptors.add(FoldingDescriptor(node, TextRange.create(range.startOffset + delta, range.endOffset - delta)))
    }
  }

  private fun foldMatchStatement(node: ASTNode, descriptors: MutableList<FoldingDescriptor?>) {
    val nodeRange = node.textRange
    if (nodeRange.isEmpty) {
      return
    }

    val elType = node.elementType
    if (elType === PyElementTypes.MATCH_STATEMENT) {
      val colon = node.findChildByType(PyTokenTypes.COLON)
      foldSegment(node, descriptors, nodeRange, colon)
    }
  }

  private fun foldSegment(node: ASTNode, descriptors: MutableList<FoldingDescriptor?>, nodeRange: TextRange, colon: ASTNode?) {
    val nodeEnd = nodeRange.endOffset
    if (colon != null && nodeEnd - (colon.startOffset + 1) > 1) {
      val chars = node.chars
      val nodeStart = nodeRange.startOffset
      val foldStart = colon.startOffset + 1
      var foldEnd = nodeEnd
      while (foldEnd > max(nodeStart, foldStart + 1) && Character.isWhitespace(chars[foldEnd - nodeStart - 1])) {
        foldEnd--
      }
      descriptors.add(FoldingDescriptor(node, TextRange(foldStart, foldEnd)))
    }
    else if (nodeRange.length > 1) { // only for ranges at least 1 char wide
      descriptors.add(FoldingDescriptor(node, nodeRange))
    }
  }

  private fun foldStatementList(node: ASTNode, descriptors: MutableList<FoldingDescriptor?>) {
    val nodeRange = node.textRange
    if (nodeRange.isEmpty) {
      return
    }

    val parentType = node.treeParent.elementType
    if (isFunction(parentType) || isClass(parentType) || checkFoldBlocks(node, parentType)) {
      val colon = node.treeParent.findChildByType(PyTokenTypes.COLON)
      foldSegment(node, descriptors, nodeRange, colon)
    }
  }

  protected open fun checkFoldBlocks(statementList: ASTNode, parentType: IElementType): Boolean {
    val element = statementList.psi
    assert(element is PyAstStatementList)

    return parentType in PyElementTypes.PARTS ||
           parentType === PyElementTypes.WITH_STATEMENT ||
           parentType === PyElementTypes.CASE_CLAUSE
  }

  private fun foldLongStrings(node: ASTNode, descriptors: MutableList<FoldingDescriptor?>) {
    //don't want to fold docstrings like """\n string \n """
    val shouldFoldDocString = getDocStringOwnerType(node) != null && StringUtil.countNewLines(node.chars) > 1
    val shouldFoldString = getDocStringOwnerType(node) == null && StringUtil.countNewLines(node.chars) > 0
    if (shouldFoldDocString || shouldFoldString) {
      descriptors.add(FoldingDescriptor(node, node.textRange))
    }
  }

  protected open fun getDocStringOwnerType(node: ASTNode): IElementType? {
    val treeParent = node.treeParent
    val parentType = treeParent.elementType
    if (parentType === PyElementTypes.EXPRESSION_STATEMENT && treeParent.treeParent != null) {
      val parent2 = treeParent.treeParent
      if (parent2.elementType === PyElementTypes.STATEMENT_LIST && parent2.treeParent != null && treeParent === parent2.firstChildNode) {
        val parent3 = parent2.treeParent.elementType
        if (isFunction(parent3) || isClass(parent3)) {
          return parent3
        }
      }
      else if (parent2 is PyAstFile) {
        return parent2.elementType
      }
    }
    return null
  }

  private fun getLanguagePlaceholderForString(stringLiteralExpression: PyAstStringLiteralExpression): String {
    val stringText = stringLiteralExpression.text
    val quotes = PyStringLiteralCoreUtil.getQuotes(stringText)
    if (quotes != null) {
      return quotes.second + "..." + quotes.second
    }
    return "..."
  }

  protected open fun isImport(node: ASTNode): Boolean {
    return node.elementType in PyElementTypes.IMPORT_STATEMENTS
  }

  protected open fun isFunction(elementType: IElementType?): Boolean {
    return elementType === PyElementTypes.FUNCTION_DECLARATION
  }

  protected open fun isClass(elementType: IElementType?): Boolean {
    return elementType === PyElementTypes.CLASS_DECLARATION
  }

  companion object {
    const val PYTHON_TYPE_ANNOTATION_GROUP_NAME: String = "Python type annotation"
  }
}

@JvmField
val FOLDABLE_COLLECTIONS_LITERALS: TokenSet = TokenSet.create(
  PyElementTypes.SET_LITERAL_EXPRESSION,
  PyElementTypes.DICT_LITERAL_EXPRESSION,
  PyElementTypes.GENERATOR_EXPRESSION,
  PyElementTypes.SET_COMP_EXPRESSION,
  PyElementTypes.DICT_COMP_EXPRESSION,
  PyElementTypes.LIST_LITERAL_EXPRESSION,
  PyElementTypes.LIST_COMP_EXPRESSION,
  PyElementTypes.TUPLE_EXPRESSION)
