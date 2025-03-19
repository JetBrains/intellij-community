// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.Cancellation
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import java.util.function.Function

abstract class PyElementTypesFacade {

  // stubs on backend side
  abstract val functionDeclaration: IElementType
  abstract val classDeclaration: IElementType
  abstract val parameterList: IElementType
  abstract val decoratorList: IElementType
  abstract val namedParameter: IElementType
  abstract val tupleParameter: IElementType
  abstract val slashParameter: IElementType
  abstract val singleStarParameter: IElementType
  abstract val decoratorCall: IElementType
  abstract val importElement: IElementType
  abstract val annotation: IElementType
  abstract val starImportElement: IElementType
  abstract val exceptPart: IElementType
  abstract val fromImportStatement: IElementType
  abstract val importStatement: IElementType
  abstract val targetExpression: IElementType
  abstract val typeParameter: IElementType
  abstract val typeParameterList: IElementType
  abstract val typeAliasStatement: IElementType

  // reparseable elements
  abstract val statementList: IElementType

  // constructors for non-stub elements
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
  abstract val boolLiteralExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val parenthesizedExpressionConstructor: Function<in ASTNode, out PsiElement>
  abstract val subscriptionExpressionConstructor: Function<in ASTNode, out PsiElement>

  abstract val sliceExpressionConstructor: Function<in ASTNode, out PsiElement>
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

  companion object {
    val INSTANCE: PyElementTypesFacade by lazy {
      Cancellation.forceNonCancellableSectionInClassInitializer {
        ApplicationManager.getApplication().service<PyElementTypesFacade>()
      }
    }
  }
}