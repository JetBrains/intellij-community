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
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.ast.*
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
        insertComma(container, lastElement)
      }
    }

    private fun processTupleExpression(node: PyAstTupleExpression<*>) {
      if ((node.parent !is PyAstParenthesizedExpression && node.parent !is PyAstSubscriptionExpression) || !node.isMultiLine()) return
      node.elements.lastOrNull()?.let { lastElement ->
        if (lastElement.nextSibling?.elementType != PyTokenTypes.COMMA) {
          insertComma(node, lastElement)
        }
      }
    }

    private fun insertComma(container: PsiElement, after: PsiElement) {
      val initialLength = container.textLength
      val comma = PyAstElementGenerator.getInstance(after.project).createComma()
      val addedComma = container.addAfter(comma.psi, after)
      codeStyleManager.reformatRange(container, after.endOffset, addedComma.endOffset)
      helper.updateResultRange(initialLength, container.textLength)
    }

    private fun PsiElement.isMultiLine(): Boolean {
      val lookForNewLineAfter = (PyPsiUtilsCore.getNextNonWhitespaceSiblingOnSameLine(this) as? PsiComment) ?: this
      return (lookForNewLineAfter.nextSibling as? PsiWhiteSpace)?.textContains('\n') == true
    }
  }
}