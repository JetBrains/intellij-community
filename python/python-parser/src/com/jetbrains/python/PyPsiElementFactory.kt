// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function

/**
 * Creates the PSI for the Python element types of [PyElementTypes].
 *
 * The element types are shared constants. The PSI classes behind them come from this application service each time an
 * element type creates an element. `intellij.python.ast` registers the AST-only PSI as the open default, and
 * `intellij.python.psi.impl` overrides it with the stub-based PSI. JetBrains Light starts with the default and switches to
 * the stub-based PSI when PythonCore loads at the move to the smart mode.
 */
@ApiStatus.Internal
abstract class PyPsiElementFactory {

  // Elements with a stub in the stub-based PSI
  abstract val functionDeclarationConstructor: Function<in ASTNode, out PsiElement>
  abstract val classDeclarationConstructor: Function<in ASTNode, out PsiElement>
  abstract val parameterListConstructor: Function<in ASTNode, out PsiElement>
  abstract val decoratorListConstructor: Function<in ASTNode, out PsiElement>
  abstract val namedParameterConstructor: Function<in ASTNode, out PsiElement>
  abstract val tupleParameterConstructor: Function<in ASTNode, out PsiElement>
  abstract val slashParameterConstructor: Function<in ASTNode, out PsiElement>
  abstract val singleStarParameterConstructor: Function<in ASTNode, out PsiElement>
  abstract val decoratorCallConstructor: Function<in ASTNode, out PsiElement>
  abstract val importElementConstructor: Function<in ASTNode, out PsiElement>
  abstract val annotationConstructor: Function<in ASTNode, out PsiElement>
  abstract val starImportElementConstructor: Function<in ASTNode, out PsiElement>
  abstract val exceptPartConstructor: Function<in ASTNode, out PsiElement>
  abstract val fromImportStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val importStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val targetExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val typeParameterConstructor: Function<in ASTNode, out PsiElement>
  abstract val typeParameterListConstructor: Function<in ASTNode, out PsiElement>
  abstract val typeAliasStatementConstructor: Function<in ASTNode, out PsiElement>

  // Elements without a stub
  abstract val argumentListConstructor: Function<in ASTNode, out PsiElement>
  abstract val printTargetConstructor: Function<in ASTNode, out PsiElement>
  abstract val decoratorConstructor: Function<in ASTNode, out PsiElement>
  abstract val expressionStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val assignmentStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val augAssignmentStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val assertStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val breakStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val continueStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val delStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val execStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val forStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val typeDeclarationStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val globalStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val ifStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val passStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val printStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val raiseStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val returnStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val tryExceptStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val withStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val whileStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val nonlocalStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val withItemConstructor: Function<in ASTNode, out PsiElement>
  abstract val emptyExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val referenceExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val integerLiteralExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val floatLiteralExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val imaginaryLiteralExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val stringLiteralExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val noneLiteralExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val ellipsisLiteralExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val boolLiteralExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val parenthesizedExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val subscriptionExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val sliceItemConstructor: Function<in ASTNode, out PsiElement>
  abstract val binaryExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val prefixExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val callExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val listLiteralExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val tupleExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val keywordArgumentExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val starArgumentExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val lambdaExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val listCompExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val dictLiteralExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val keyValueExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val reprExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val generatorExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val conditionalExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val yieldExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val starExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val doubleStarExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val assignmentExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val setLiteralExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val setCompExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val dictCompExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val ifPartIfConstructor: Function<in ASTNode, out PsiElement>
  abstract val ifPartElifConstructor: Function<in ASTNode, out PsiElement>
  abstract val forPartConstructor: Function<in ASTNode, out PsiElement>
  abstract val whilePartConstructor: Function<in ASTNode, out PsiElement>
  abstract val tryPartConstructor: Function<in ASTNode, out PsiElement>
  abstract val finallyPartConstructor: Function<in ASTNode, out PsiElement>
  abstract val elsePartConstructor: Function<in ASTNode, out PsiElement>
  abstract val fStringNodeConstructor: Function<in ASTNode, out PsiElement>
  abstract val fStringFragmentConstructor: Function<in ASTNode, out PsiElement>
  abstract val fStringFragmentFormatPartConstructor: Function<in ASTNode, out PsiElement>
  abstract val matchStatementConstructor: Function<in ASTNode, out PsiElement>
  abstract val caseClauseConstructor: Function<in ASTNode, out PsiElement>
  abstract val literalPatternConstructor: Function<in ASTNode, out PsiElement>
  abstract val valuePatternConstructor: Function<in ASTNode, out PsiElement>
  abstract val capturePatternConstructor: Function<in ASTNode, out PsiElement>
  abstract val wildcardPatternConstructor: Function<in ASTNode, out PsiElement>
  abstract val groupPatternConstructor: Function<in ASTNode, out PsiElement>
  abstract val sequencePatternConstructor: Function<in ASTNode, out PsiElement>
  abstract val singleStarPatternConstructor: Function<in ASTNode, out PsiElement>
  abstract val doubleStarPatternConstructor: Function<in ASTNode, out PsiElement>
  abstract val mappingPatternConstructor: Function<in ASTNode, out PsiElement>
  abstract val keyValuePatternConstructor: Function<in ASTNode, out PsiElement>
  abstract val classPatternConstructor: Function<in ASTNode, out PsiElement>
  abstract val patternArgumentListConstructor: Function<in ASTNode, out PsiElement>
  abstract val keywordPatternConstructor: Function<in ASTNode, out PsiElement>
  abstract val orPatternConstructor: Function<in ASTNode, out PsiElement>
  abstract val asPatternConstructor: Function<in ASTNode, out PsiElement>

  /** The PSI file of a `.py` file. */
  abstract fun createPythonFile(viewProvider: FileViewProvider): PsiFile

  /** The PSI file of a `.pyi` file. */
  abstract fun createPyiFile(viewProvider: FileViewProvider): PsiFile

  /**
   * The node of a statement list of [type]. With [text] the node is lazy-parseable. Without it the parser fills the node.
   * The node is also the PSI element.
   */
  abstract fun createStatementListNode(type: IElementType, text: CharSequence?): ASTNode

  /** The leaf of a string token of [type]. */
  abstract fun createStringLeaf(type: IElementType, text: CharSequence): LeafElement

  /**
   * Parses the contents of a file node in a way that the default parse does not cover, for example a console file.
   * Returns `null` when the default parse applies.
   */
  open fun parseFileContents(node: ASTNode, languageLevel: LanguageLevel): ASTNode? = null

  companion object {
    /** The registered factory: the stub-based one when `intellij.python.psi.impl` is loaded, else the AST-only default. */
    @JvmStatic
    fun current(): PyPsiElementFactory = ApplicationManager.getApplication().service<PyPsiElementFactory>()
  }
}
