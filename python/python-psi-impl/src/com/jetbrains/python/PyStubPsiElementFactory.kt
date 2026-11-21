// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.PsiBuilderFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType
import com.jetbrains.python.parsing.console.PyConsoleParser
import com.jetbrains.python.parsing.console.PythonConsoleLexer
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.impl.PyAnnotationImpl
import com.jetbrains.python.psi.impl.PyArgumentListImpl
import com.jetbrains.python.psi.impl.PyAsPatternImpl
import com.jetbrains.python.psi.impl.PyAssertStatementImpl
import com.jetbrains.python.psi.impl.PyAssignmentExpressionImpl
import com.jetbrains.python.psi.impl.PyAssignmentStatementImpl
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementImpl
import com.jetbrains.python.psi.impl.PyBinaryExpressionImpl
import com.jetbrains.python.psi.impl.PyBoolLiteralExpressionImpl
import com.jetbrains.python.psi.impl.PyBreakStatementImpl
import com.jetbrains.python.psi.impl.PyCallExpressionImpl
import com.jetbrains.python.psi.impl.PyCapturePatternImpl
import com.jetbrains.python.psi.impl.PyCaseClauseImpl
import com.jetbrains.python.psi.impl.PyClassImpl
import com.jetbrains.python.psi.impl.PyClassPatternImpl
import com.jetbrains.python.psi.impl.PyConditionalExpressionImpl
import com.jetbrains.python.psi.impl.PyContinueStatementImpl
import com.jetbrains.python.psi.impl.PyDecoratorImpl
import com.jetbrains.python.psi.impl.PyDecoratorListImpl
import com.jetbrains.python.psi.impl.PyDelStatementImpl
import com.jetbrains.python.psi.impl.PyDictCompExpressionImpl
import com.jetbrains.python.psi.impl.PyDictLiteralExpressionImpl
import com.jetbrains.python.psi.impl.PyDoubleStarExpressionImpl
import com.jetbrains.python.psi.impl.PyDoubleStarPatternImpl
import com.jetbrains.python.psi.impl.PyEllipsisLiteralExpressionImpl
import com.jetbrains.python.psi.impl.PyElsePartImpl
import com.jetbrains.python.psi.impl.PyEmptyExpressionImpl
import com.jetbrains.python.psi.impl.PyExceptPartImpl
import com.jetbrains.python.psi.impl.PyExecStatementImpl
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl
import com.jetbrains.python.psi.impl.PyExpressionStatementImpl
import com.jetbrains.python.psi.impl.PyFStringFragmentFormatPartImpl
import com.jetbrains.python.psi.impl.PyFStringFragmentImpl
import com.jetbrains.python.psi.impl.PyFileImpl
import com.jetbrains.python.psi.impl.PyFinallyPartImpl
import com.jetbrains.python.psi.impl.PyForPartImpl
import com.jetbrains.python.psi.impl.PyForStatementImpl
import com.jetbrains.python.psi.impl.PyFormattedStringElementImpl
import com.jetbrains.python.psi.impl.PyFromImportStatementImpl
import com.jetbrains.python.psi.impl.PyFunctionImpl
import com.jetbrains.python.psi.impl.PyGeneratorExpressionImpl
import com.jetbrains.python.psi.impl.PyGlobalStatementImpl
import com.jetbrains.python.psi.impl.PyGroupPatternImpl
import com.jetbrains.python.psi.impl.PyIfPartElifImpl
import com.jetbrains.python.psi.impl.PyIfPartIfImpl
import com.jetbrains.python.psi.impl.PyIfStatementImpl
import com.jetbrains.python.psi.impl.PyImportElementImpl
import com.jetbrains.python.psi.impl.PyImportStatementImpl
import com.jetbrains.python.psi.impl.PyKeyValueExpressionImpl
import com.jetbrains.python.psi.impl.PyKeyValuePatternImpl
import com.jetbrains.python.psi.impl.PyKeywordArgumentImpl
import com.jetbrains.python.psi.impl.PyKeywordPatternImpl
import com.jetbrains.python.psi.impl.PyLambdaExpressionImpl
import com.jetbrains.python.psi.impl.PyListCompExpressionImpl
import com.jetbrains.python.psi.impl.PyListLiteralExpressionImpl
import com.jetbrains.python.psi.impl.PyLiteralPatternImpl
import com.jetbrains.python.psi.impl.PyMappingPatternImpl
import com.jetbrains.python.psi.impl.PyMatchStatementImpl
import com.jetbrains.python.psi.impl.PyNamedParameterImpl
import com.jetbrains.python.psi.impl.PyNoneLiteralExpressionImpl
import com.jetbrains.python.psi.impl.PyNonlocalStatementImpl
import com.jetbrains.python.psi.impl.PyNumericLiteralExpressionImpl
import com.jetbrains.python.psi.impl.PyOrPatternImpl
import com.jetbrains.python.psi.impl.PyParameterListImpl
import com.jetbrains.python.psi.impl.PyParenthesizedExpressionImpl
import com.jetbrains.python.psi.impl.PyPassStatementImpl
import com.jetbrains.python.psi.impl.PyPatternArgumentListImpl
import com.jetbrains.python.psi.impl.PyPlainStringElementImpl
import com.jetbrains.python.psi.impl.PyPrefixExpressionImpl
import com.jetbrains.python.psi.impl.PyPrintStatementImpl
import com.jetbrains.python.psi.impl.PyPrintTargetImpl
import com.jetbrains.python.psi.impl.PyRaiseStatementImpl
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl
import com.jetbrains.python.psi.impl.PyReprExpressionImpl
import com.jetbrains.python.psi.impl.PyReturnStatementImpl
import com.jetbrains.python.psi.impl.PySequencePatternImpl
import com.jetbrains.python.psi.impl.PySetCompExpressionImpl
import com.jetbrains.python.psi.impl.PySetLiteralExpressionImpl
import com.jetbrains.python.psi.impl.PySingleStarParameterImpl
import com.jetbrains.python.psi.impl.PySingleStarPatternImpl
import com.jetbrains.python.psi.impl.PySlashParameterImpl
import com.jetbrains.python.psi.impl.PySliceItemImpl
import com.jetbrains.python.psi.impl.PyStarArgumentImpl
import com.jetbrains.python.psi.impl.PyStarExpressionImpl
import com.jetbrains.python.psi.impl.PyStarImportElementImpl
import com.jetbrains.python.psi.impl.PyStatementListImpl
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl
import com.jetbrains.python.psi.impl.PySubscriptionExpressionImpl
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl
import com.jetbrains.python.psi.impl.PyTryExceptStatementImpl
import com.jetbrains.python.psi.impl.PyTryPartImpl
import com.jetbrains.python.psi.impl.PyTupleExpressionImpl
import com.jetbrains.python.psi.impl.PyTupleParameterImpl
import com.jetbrains.python.psi.impl.PyTypeAliasStatementImpl
import com.jetbrains.python.psi.impl.PyTypeParameterImpl
import com.jetbrains.python.psi.impl.PyTypeParameterListImpl
import com.jetbrains.python.psi.impl.PyValuePatternImpl
import com.jetbrains.python.psi.impl.PyWhilePartImpl
import com.jetbrains.python.psi.impl.PyWhileStatementImpl
import com.jetbrains.python.psi.impl.PyWildcardPatternImpl
import com.jetbrains.python.psi.impl.PyWithItemImpl
import com.jetbrains.python.psi.impl.PyWithStatementImpl
import com.jetbrains.python.psi.impl.PyYieldExpressionImpl
import com.jetbrains.python.pyi.PyiFile
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function

private typealias F = Function<in ASTNode, out PsiElement>

@ApiStatus.Internal
class PyStubPsiElementFactory : PyPsiElementFactory() {

  // Elements with a stub
  override val functionDeclarationConstructor: F = F { node -> PyFunctionImpl(node) }
  override val classDeclarationConstructor: F = F { node -> PyClassImpl(node) }
  override val parameterListConstructor: F = F { node -> PyParameterListImpl(node) }
  override val decoratorListConstructor: F = F { node -> PyDecoratorListImpl(node) }
  override val namedParameterConstructor: F = F { node -> PyNamedParameterImpl(node) }
  override val tupleParameterConstructor: F = F { node -> PyTupleParameterImpl(node) }
  override val slashParameterConstructor: F = F { node -> PySlashParameterImpl(node) }
  override val singleStarParameterConstructor: F = F { node -> PySingleStarParameterImpl(node) }
  override val decoratorCallConstructor: F = F { node -> PyDecoratorImpl(node) }
  override val importElementConstructor: F = F { node -> PyImportElementImpl(node) }
  override val annotationConstructor: F = F { node -> PyAnnotationImpl(node) }
  override val starImportElementConstructor: F = F { node -> PyStarImportElementImpl(node) }
  override val exceptPartConstructor: F = F { node -> PyExceptPartImpl(node) }
  override val fromImportStatementConstructor: F = F { node -> PyFromImportStatementImpl(node) }
  override val importStatementConstructor: F = F { node -> PyImportStatementImpl(node) }
  override val targetExpressionConstructor: F = F { node -> PyTargetExpressionImpl(node) }
  override val typeParameterConstructor: F = F { node -> PyTypeParameterImpl(node) }
  override val typeParameterListConstructor: F = F { node -> PyTypeParameterListImpl(node) }
  override val typeAliasStatementConstructor: F = F { node -> PyTypeAliasStatementImpl(node) }

  // Elements without a stub
  override val argumentListConstructor: F = F { node -> PyArgumentListImpl(node) }
  override val printTargetConstructor: F = F { node -> PyPrintTargetImpl(node) }
  override val decoratorConstructor: F = decoratorCallConstructor
  override val expressionStatementConstructor: F = F { node -> PyExpressionStatementImpl(node) }
  override val assignmentStatementConstructor: F = F { node -> PyAssignmentStatementImpl(node) }
  override val augAssignmentStatementConstructor: F = F { node -> PyAugAssignmentStatementImpl(node) }
  override val assertStatementConstructor: F = F { node -> PyAssertStatementImpl(node) }
  override val breakStatementConstructor: F = F { node -> PyBreakStatementImpl(node) }
  override val continueStatementConstructor: F = F { node -> PyContinueStatementImpl(node) }
  override val delStatementConstructor: F = F { node -> PyDelStatementImpl(node) }
  override val execStatementConstructor: F = F { node -> PyExecStatementImpl(node) }
  override val forStatementConstructor: F = F { node -> PyForStatementImpl(node) }
  override val typeDeclarationStatementConstructor: F = F { node -> PyTypeDeclarationStatementImpl(node) }
  override val globalStatementConstructor: F = F { node -> PyGlobalStatementImpl(node) }
  override val ifStatementConstructor: F = F { node -> PyIfStatementImpl(node) }
  override val passStatementConstructor: F = F { node -> PyPassStatementImpl(node) }
  override val printStatementConstructor: F = F { node -> PyPrintStatementImpl(node) }
  override val raiseStatementConstructor: F = F { node -> PyRaiseStatementImpl(node) }
  override val returnStatementConstructor: F = F { node -> PyReturnStatementImpl(node) }
  override val tryExceptStatementConstructor: F = F { node -> PyTryExceptStatementImpl(node) }
  override val withStatementConstructor: F = F { node -> PyWithStatementImpl(node) }
  override val whileStatementConstructor: F = F { node -> PyWhileStatementImpl(node) }
  override val nonlocalStatementConstructor: F = F { node -> PyNonlocalStatementImpl(node) }
  override val withItemConstructor: F = F { node -> PyWithItemImpl(node) }
  override val emptyExpressionConstructor: F = F { node -> PyEmptyExpressionImpl(node) }
  override val referenceExpressionConstructor: F = F { node -> PyReferenceExpressionImpl(node) }
  override val integerLiteralExpressionConstructor: F = F { node -> PyNumericLiteralExpressionImpl(node) }
  override val floatLiteralExpressionConstructor: F = integerLiteralExpressionConstructor
  override val imaginaryLiteralExpressionConstructor: F = integerLiteralExpressionConstructor
  override val stringLiteralExpressionConstructor: F = F { node -> PyStringLiteralExpressionImpl(node) }
  override val noneLiteralExpressionConstructor: F = F { node -> PyNoneLiteralExpressionImpl(node) }
  override val ellipsisLiteralExpressionConstructor: F = F { node -> PyEllipsisLiteralExpressionImpl(node) }
  override val boolLiteralExpressionConstructor: F = F { node -> PyBoolLiteralExpressionImpl(node) }
  override val parenthesizedExpressionConstructor: F = F { node -> PyParenthesizedExpressionImpl(node) }
  override val subscriptionExpressionConstructor: F = F { node -> PySubscriptionExpressionImpl(node) }
  override val sliceItemConstructor: F = F { node -> PySliceItemImpl(node) }
  override val binaryExpressionConstructor: F = F { node -> PyBinaryExpressionImpl(node) }
  override val prefixExpressionConstructor: F = F { node -> PyPrefixExpressionImpl(node) }
  override val callExpressionConstructor: F = F { node -> PyCallExpressionImpl(node) }
  override val listLiteralExpressionConstructor: F = F { node -> PyListLiteralExpressionImpl(node) }
  override val tupleExpressionConstructor: F = F { node -> PyTupleExpressionImpl(node) }
  override val keywordArgumentExpressionConstructor: F = F { node -> PyKeywordArgumentImpl(node) }
  override val starArgumentExpressionConstructor: F = F { node -> PyStarArgumentImpl(node) }
  override val lambdaExpressionConstructor: F = F { node -> PyLambdaExpressionImpl(node) }
  override val listCompExpressionConstructor: F = F { node -> PyListCompExpressionImpl(node) }
  override val dictLiteralExpressionConstructor: F = F { node -> PyDictLiteralExpressionImpl(node) }
  override val keyValueExpressionConstructor: F = F { node -> PyKeyValueExpressionImpl(node) }
  override val reprExpressionConstructor: F = F { node -> PyReprExpressionImpl(node) }
  override val generatorExpressionConstructor: F = F { node -> PyGeneratorExpressionImpl(node) }
  override val conditionalExpressionConstructor: F = F { node -> PyConditionalExpressionImpl(node) }
  override val yieldExpressionConstructor: F = F { node -> PyYieldExpressionImpl(node) }
  override val starExpressionConstructor: F = F { node -> PyStarExpressionImpl(node) }
  override val doubleStarExpressionConstructor: F = F { node -> PyDoubleStarExpressionImpl(node) }
  override val assignmentExpressionConstructor: F = F { node -> PyAssignmentExpressionImpl(node) }
  override val setLiteralExpressionConstructor: F = F { node -> PySetLiteralExpressionImpl(node) }
  override val setCompExpressionConstructor: F = F { node -> PySetCompExpressionImpl(node) }
  override val dictCompExpressionConstructor: F = F { node -> PyDictCompExpressionImpl(node) }
  override val ifPartIfConstructor: F = F { node -> PyIfPartIfImpl(node) }
  override val ifPartElifConstructor: F = F { node -> PyIfPartElifImpl(node) }
  override val forPartConstructor: F = F { node -> PyForPartImpl(node) }
  override val whilePartConstructor: F = F { node -> PyWhilePartImpl(node) }
  override val tryPartConstructor: F = F { node -> PyTryPartImpl(node) }
  override val finallyPartConstructor: F = F { node -> PyFinallyPartImpl(node) }
  override val elsePartConstructor: F = F { node -> PyElsePartImpl(node) }
  override val fStringNodeConstructor: F = F { node -> PyFormattedStringElementImpl(node) }
  override val fStringFragmentConstructor: F = F { node -> PyFStringFragmentImpl(node) }
  override val fStringFragmentFormatPartConstructor: F = F { node -> PyFStringFragmentFormatPartImpl(node) }
  override val matchStatementConstructor: F = F { node -> PyMatchStatementImpl(node) }
  override val caseClauseConstructor: F = F { node -> PyCaseClauseImpl(node) }
  override val literalPatternConstructor: F = F { node -> PyLiteralPatternImpl(node) }
  override val valuePatternConstructor: F = F { node -> PyValuePatternImpl(node) }
  override val capturePatternConstructor: F = F { node -> PyCapturePatternImpl(node) }
  override val wildcardPatternConstructor: F = F { node -> PyWildcardPatternImpl(node) }
  override val groupPatternConstructor: F = F { node -> PyGroupPatternImpl(node) }
  override val sequencePatternConstructor: F = F { node -> PySequencePatternImpl(node) }
  override val singleStarPatternConstructor: F = F { node -> PySingleStarPatternImpl(node) }
  override val doubleStarPatternConstructor: F = F { node -> PyDoubleStarPatternImpl(node) }
  override val mappingPatternConstructor: F = F { node -> PyMappingPatternImpl(node) }
  override val keyValuePatternConstructor: F = F { node -> PyKeyValuePatternImpl(node) }
  override val classPatternConstructor: F = F { node -> PyClassPatternImpl(node) }
  override val patternArgumentListConstructor: F = F { node -> PyPatternArgumentListImpl(node) }
  override val keywordPatternConstructor: F = F { node -> PyKeywordPatternImpl(node) }
  override val orPatternConstructor: F = F { node -> PyOrPatternImpl(node) }
  override val asPatternConstructor: F = F { node -> PyAsPatternImpl(node) }

  override fun createPythonFile(viewProvider: FileViewProvider): PsiFile = PyFileImpl(viewProvider)

  override fun createPyiFile(viewProvider: FileViewProvider): PsiFile = PyiFile(viewProvider)

  override fun createStatementListNode(type: IElementType, text: CharSequence?): ASTNode = PyStatementListImpl(type, text)

  override fun createStringLeaf(type: IElementType, text: CharSequence): LeafElement = PyPlainStringElementImpl(type, text)

  override fun createExpressionCodeFragment(project: Project, name: String, text: String, isPhysical: Boolean): PsiFile =
    PyExpressionCodeFragmentImpl(project, name, text, isPhysical)

  /** Parses a console file with the console lexer and parser. */
  override fun parseFileContents(node: ASTNode, languageLevel: LanguageLevel): ASTNode? {
    val consoleData = PythonRuntimeService.getInstance()?.getPythonConsoleData(node) ?: return null
    val psi = node.psi ?: return null
    val language = node.elementType.language
    val lexer = if (consoleData.isIPythonEnabled) {
      PythonConsoleLexer()
    }
    else {
      LanguageParserDefinitions.INSTANCE.forLanguage(language)?.createLexer(psi.project) ?: return null
    }
    val builder = PsiBuilderFactory.getInstance().createBuilder(psi.project, node, lexer, language, node.chars)
    return PyConsoleParser(consoleData, languageLevel).parse(node.elementType, builder).firstChildNode
  }
}
