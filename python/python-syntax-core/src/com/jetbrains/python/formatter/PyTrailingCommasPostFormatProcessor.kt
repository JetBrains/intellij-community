package com.jetbrains.python.formatter

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessorHelper
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.ast.PyAstArgumentList
import com.jetbrains.python.ast.PyAstDictLiteralExpression
import com.jetbrains.python.ast.PyAstElement
import com.jetbrains.python.ast.PyAstListLiteralExpression
import com.jetbrains.python.ast.PyAstParameterList
import com.jetbrains.python.ast.PyAstParenthesizedExpression
import com.jetbrains.python.ast.PyAstRecursiveElementVisitor
import com.jetbrains.python.ast.PyAstSequenceExpression
import com.jetbrains.python.ast.PyAstSetLiteralExpression
import com.jetbrains.python.ast.PyAstSubscriptionExpression
import com.jetbrains.python.ast.PyAstTupleExpression
import com.jetbrains.python.ast.impl.PyPsiUtilsCore
import com.jetbrains.python.psi.PyAstElementGenerator

class PyTrailingCommasPostFormatProcessor : PostFormatProcessor {

  private fun processTextRange(psiElement: PsiElement, range: TextRange, settings: CodeStyleSettings): TextRange {
    if (psiElement.language != PythonLanguage.getInstance()) return range

    PyPsiUtilsCore.assertValid(psiElement)

    val psiFile = if (psiElement.isValid()) psiElement.getContainingFile() else null
    if (psiFile == null) return range
    val project = psiFile.getProject()
    val visitor = TrailingCommaInsertVisitor(settings, range, project)

    psiElement.accept(visitor)
    visitor.insertCommas()
    return visitor.resultTextRange
  }

  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
    processTextRange(source, source.getTextRange(), settings)
    return source
  }

  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    return processTextRange(source, rangeToReformat, settings)
  }

  private class TrailingCommaInsertVisitor(
    settings: CodeStyleSettings,
    range: TextRange,
    project: Project,
  ) : PyAstRecursiveElementVisitor() {

    private val pySettings = settings.getCustomSettings(PyCodeStyleSettings::class.java)
    private val pyCommonSettings = settings.getCommonSettings(PythonLanguage.getInstance())
    private val helper = PostFormatProcessorHelper(pyCommonSettings).apply { resultTextRange = range }
    private val codeStyleManager = CodeStyleManager.getInstance(project)
    private val commaPlaces = mutableListOf<CommaPlace>()

    val resultTextRange: TextRange get() = helper.resultTextRange

    override fun visitPyDictLiteralExpression(node: PyAstDictLiteralExpression) {
      processCollectionLiteral(node)
      super.visitPyDictLiteralExpression(node)
    }

    override fun visitPyListLiteralExpression(node: PyAstListLiteralExpression) {
      processCollectionLiteral(node)
      super.visitPyListLiteralExpression(node)
    }

    override fun visitPySetLiteralExpression(node: PyAstSetLiteralExpression) {
      processCollectionLiteral(node)
      super.visitPySetLiteralExpression(node)
    }

    override fun visitPyParameterList(node: PyAstParameterList) {
      if (pySettings.USE_TRAILING_COMMA_IN_PARAMETER_LIST) {
        processContainer(node, node.parameters.asList())
      }
      super.visitPyParameterList(node)
    }

    override fun visitPyArgumentList(node: PyAstArgumentList) {
      if (pySettings.USE_TRAILING_COMMA_IN_ARGUMENTS_LIST) {
        processContainer(node, node.arguments.asList())
      }
      super.visitPyArgumentList(node)
    }

    override fun visitPyTupleExpression(node: PyAstTupleExpression<*>) {
      if (pySettings.USE_TRAILING_COMMA_IN_COLLECTIONS && helper.isElementFullyInRange(node)) {
        processTupleExpression(node)
      }
      super.visitPyTupleExpression(node)
    }

    private fun processCollectionLiteral(node: PyAstSequenceExpression) {
      if (pySettings.USE_TRAILING_COMMA_IN_COLLECTIONS) {
        processContainer(node, node.elements.asList())
      }
    }

    private fun processContainer(container: PsiElement, elements: List<PyAstElement>) {
      if (!helper.isElementFullyInRange(container)) return
      elements.lastOrNull()?.let { lastElement ->
        if (!lastElement.isMultiLine()) return
        addCommaPlace(container, lastElement)
      }
    }

    private fun processTupleExpression(node: PyAstTupleExpression<*>) {
      if ((node.parent !is PyAstParenthesizedExpression && node.parent !is PyAstSubscriptionExpression) || !node.isMultiLine()) return
      node.elements.lastOrNull()?.let { lastElement ->
        if (lastElement.nextSibling?.elementType != PyTokenTypes.COMMA) {
          addCommaPlace(node, lastElement)
        }
      }
    }

    private fun addCommaPlace(container: PsiElement, after: PsiElement) {
      commaPlaces.add(CommaPlace(container, after))
    }

    /**
     * Inserts all commas that the visit found. Then reformats each group of nearby commas in one pass.
     * The visit does not change the tree.
     */
    fun insertCommas() {
      val generator = commaPlaces.firstOrNull()?.let { PyAstElementGenerator.getInstance(it.container.project) } ?: return
      for (group in groupNearbyContainers()) {
        val root = PsiTreeUtil.findCommonParent(group.map { it.container }) ?: continue
        val initialLength = root.textLength
        val addedCommas = group.map { (container, after) -> container.addAfter(generator.createComma().psi, after) }
        val formatted = codeStyleManager.reformatRange(root, addedCommas.minOf { it.startOffset }, addedCommas.maxOf { it.endOffset }, true)
        helper.updateResultRange(initialLength, formatted.textLength)
      }
    }

    /**
     * Puts the next container into the current group when the text between them is at most
     * 1/[MAX_REFORMAT_GROUPS] of the file. Then the groups reformat at most the full file again,
     * and there are at most [MAX_REFORMAT_GROUPS] + 1 groups.
     */
    private fun groupNearbyContainers(): List<List<CommaPlace>> {
      val maxGap = commaPlaces.first().container.containingFile.textLength / MAX_REFORMAT_GROUPS
      val groups = mutableListOf<MutableList<CommaPlace>>()
      var groupEnd = 0
      for (place in commaPlaces.sortedBy { it.container.startOffset }) {
        val range = place.container.textRange
        if (groups.isEmpty() || range.startOffset - groupEnd > maxGap) {
          groups.add(mutableListOf())
        }
        groupEnd = maxOf(groupEnd, range.endOffset)
        groups.last().add(place)
      }
      return groups
    }

    private fun PsiElement.isMultiLine(): Boolean {
      val lookForNewLineAfter = (PyPsiUtilsCore.getNextNonWhitespaceSiblingOnSameLine(this) as? PsiComment) ?: this
      return (lookForNewLineAfter.nextSibling as? PsiWhiteSpace)?.textContains('\n') == true
    }
  }

  private data class CommaPlace(val container: PsiElement, val after: PsiElement)
}

/**
 * Each reformat call scans the full file, for example for the formatter tags.
 * A reformat of a group also formats the text between its commas again.
 * This limit keeps both costs linear in the file length.
 */
private const val MAX_REFORMAT_GROUPS = 32