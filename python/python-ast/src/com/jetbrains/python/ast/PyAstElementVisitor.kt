package com.jetbrains.python.ast

import com.intellij.psi.PsiElementVisitor

/**
 * Visitor for python-specific nodes.
 */
open class PyAstElementVisitor : PsiElementVisitor() {
  open fun visitPyElement(node: PyAstElement) {
    visitElement(node)
  }

  open fun visitPyReferenceExpression(node: PyAstReferenceExpression) {
    visitPyExpression(node)
  }

  open fun visitPyTargetExpression(node: PyAstTargetExpression) {
    visitPyExpression(node)
  }

  open fun visitPyCallExpression(node: PyAstCallExpression) {
    visitPyExpression(node)
  }

  open fun visitPyDecoratorList(node: PyAstDecoratorList) {
    visitPyElement(node)
  }

  open fun visitPyDecorator(node: PyAstDecorator) {
    visitPyElement(node)
  }

  open fun visitPyComprehensionElement(node: PyAstComprehensionElement) {
    visitPyExpression(node)
  }

  open fun visitPyGeneratorExpression(node: PyAstGeneratorExpression) {
    visitPyComprehensionElement(node)
  }

  open fun visitPyBinaryExpression(node: PyAstBinaryExpression) {
    visitPyExpression(node)
  }

  open fun visitPyPrefixExpression(node: PyAstPrefixExpression) {
    visitPyExpression(node)
  }

  open fun visitPySequenceExpression(node: PyAstSequenceExpression) {
    visitPyExpression(node)
  }

  open fun visitPyTupleExpression(node: PyAstTupleExpression<*>) {
    visitPySequenceExpression(node)
  }

  open fun visitPyParenthesizedExpression(node: PyAstParenthesizedExpression) {
    visitPyExpression(node)
  }

  open fun visitPyDictLiteralExpression(node: PyAstDictLiteralExpression) {
    visitPyExpression(node)
  }

  open fun visitPyListLiteralExpression(node: PyAstListLiteralExpression) {
    visitPySequenceExpression(node)
  }

  open fun visitPySetLiteralExpression(node: PyAstSetLiteralExpression) {
    visitPySequenceExpression(node)
  }

  open fun visitPyListCompExpression(node: PyAstListCompExpression) {
    visitPyComprehensionElement(node)
  }

  open fun visitPyDictCompExpression(node: PyAstDictCompExpression) {
    visitPyComprehensionElement(node)
  }

  open fun visitPySetCompExpression(node: PyAstSetCompExpression) {
    visitPyComprehensionElement(node)
  }

  open fun visitPyLambdaExpression(node: PyAstLambdaExpression) {
    visitPyExpression(node)
  }

  open fun visitPyAssignmentStatement(node: PyAstAssignmentStatement) {
    visitPyStatement(node)
  }

  open fun visitPyAugAssignmentStatement(node: PyAstAugAssignmentStatement) {
    visitPyStatement(node)
  }

  open fun visitPyDelStatement(node: PyAstDelStatement) {
    visitPyStatement(node)
  }

  open fun visitPyReturnStatement(node: PyAstReturnStatement) {
    visitPyStatement(node)
  }
  
  open fun visitPyPassStatement(node: PyAstPassStatement) {
    visitPyStatement(node)
  }

  open fun visitPyYieldExpression(node: PyAstYieldExpression) {
    visitPyExpression(node)
  }

  open fun visitPyTryExceptStatement(node: PyAstTryExceptStatement) {
    visitPyStatement(node)
  }

  open fun visitPyRaiseStatement(node: PyAstRaiseStatement) {
    visitPyStatement(node)
  }

  open fun visitPyBreakStatement(node: PyAstBreakStatement) {
    visitPyStatement(node)
  }

  open fun visitPyContinueStatement(node: PyAstContinueStatement) {
    visitPyStatement(node)
  }

  open fun visitPyGlobalStatement(node: PyAstGlobalStatement) {
    visitPyStatement(node)
  }

  open fun visitPyFromImportStatement(node: PyAstFromImportStatement) {
    visitPyStatement(node)
  }

  open fun visitPyIfStatement(node: PyAstIfStatement) {
    visitPyStatement(node)
  }

  open fun visitPyForStatement(node: PyAstForStatement) {
    visitPyStatement(node)
  }

  open fun visitPyWhileStatement(node: PyAstWhileStatement) {
    visitPyStatement(node)
  }

  open fun visitPyWithStatement(node: PyAstWithStatement) {
    visitPyStatement(node)
  }

  open fun visitPyExpressionStatement(node: PyAstExpressionStatement) {
    visitPyStatement(node)
  }

  open fun visitPyStatement(node: PyAstStatement) {
    visitPyElement(node)
  }

  open fun visitPyExpression(node: PyAstExpression) {
    visitPyElement(node)
  }

  open fun visitPyParameterList(node: PyAstParameterList) {
    visitPyElement(node)
  }

  open fun visitPyParameter(node: PyAstParameter) {
    visitPyElement(node)
  }

  open fun visitPyNamedParameter(node: PyAstNamedParameter) {
    visitPyParameter(node)
  }

  open fun visitPyTupleParameter(node: PyAstTupleParameter) {
    visitPyParameter(node)
  }

  open fun visitPyArgumentList(node: PyAstArgumentList) {
    visitPyElement(node)
  }

  open fun visitPyStatementList(node: PyAstStatementList) {
    visitPyElement(node)
  }

  open fun visitPyExceptBlock(node: PyAstExceptPart) {
    visitPyElement(node)
  }

  open fun visitPyFunction(node: PyAstFunction) {
    visitPyElement(node)
  }

  open fun visitPyClass(node: PyAstClass) {
    visitPyElement(node)
  }

  open fun visitPyFile(node: PyAstFile) {
    visitPyElement(node)
  }

  open fun visitPyStringLiteralExpression(node: PyAstStringLiteralExpression) {
    visitPyElement(node)
  }

  open fun visitPyFormattedStringElement(node: PyAstFormattedStringElement) {
    visitPyElement(node)
  }

  open fun visitPyFStringFragment(node: PyAstFStringFragment) {
    visitPyElement(node)
  }

  open fun visitPyNumericLiteralExpression(node: PyAstNumericLiteralExpression) {
    visitPyElement(node)
  }

  open fun visitPyPrintStatement(node: PyAstPrintStatement) {
    visitPyStatement(node)
  }

  open fun visitPyImportStatement(node: PyAstImportStatement) {
    visitPyStatement(node)
  }

  open fun visitPyReprExpression(node: PyAstReprExpression) {
    visitPyExpression(node)
  }

  open fun visitPyNonlocalStatement(node: PyAstNonlocalStatement) {
    visitPyStatement(node)
  }

  open fun visitPyStarExpression(node: PyAstStarExpression) {
    visitPyExpression(node)
  }

  open fun visitPyDoubleStarExpression(node: PyAstDoubleStarExpression) {
    visitPyExpression(node)
  }

  open fun visitPySubscriptionExpression(node: PyAstSubscriptionExpression) {
    visitPyExpression(node)
  }

  open fun visitPyImportElement(node: PyAstImportElement) {
    visitPyElement(node)
  }

  open fun visitPyStarImportElement(node: PyAstStarImportElement) {
    visitPyElement(node)
  }

  open fun visitPyConditionalStatementPart(node: PyAstConditionalStatementPart) {
    visitPyElement(node)
  }

  open fun visitPyAssertStatement(node: PyAstAssertStatement) {
    visitPyElement(node)
  }

  open fun visitPyNoneLiteralExpression(node: PyAstNoneLiteralExpression) {
    visitPyElement(node)
  }

  open fun visitPyBoolLiteralExpression(node: PyAstBoolLiteralExpression) {
    visitPyElement(node)
  }

  open fun visitPyConditionalExpression(node: PyAstConditionalExpression) {
    visitPyElement(node)
  }

  open fun visitPyKeywordArgument(node: PyAstKeywordArgument) {
    visitPyElement(node)
  }

  open fun visitPyWithItem(node: PyAstWithItem) {
    visitPyElement(node)
  }

  open fun visitPyTypeDeclarationStatement(node: PyAstTypeDeclarationStatement) {
    visitPyStatement(node)
  }

  open fun visitPyAnnotation(node: PyAstAnnotation) {
    visitPyElement(node)
  }

  open fun visitPySlashParameter(node: PyAstSlashParameter) {
    visitPyElement(node)
  }

  open fun visitPySingleStarParameter(node: PyAstSingleStarParameter) {
    visitPyElement(node)
  }

  open fun visitPyAssignmentExpression(node: PyAstAssignmentExpression) {
    visitPyExpression(node)
  }

  open fun visitPyPattern(node: PyAstPattern) {
    visitPyElement(node)
  }

  open fun visitPyAsPattern(node: PyAstAsPattern) {
    visitPyPattern(node)
  }

  open fun visitPyCapturePattern(node: PyAstCapturePattern) {
    visitPyPattern(node)
  }

  open fun visitWildcardPattern(node: PyAstWildcardPattern) {
    visitPyPattern(node)
  }

  open fun visitPyClassPattern(node: PyAstClassPattern) {
    visitPyPattern(node)
  }

  open fun visitPyDoubleStarPattern(node: PyAstDoubleStarPattern) {
    visitPyPattern(node)
  }

  open fun visitPySingleStarPattern(node: PyAstSingleStarPattern) {
    visitPyPattern(node)
  }

  open fun visitPyGroupPattern(node: PyAstGroupPattern) {
    visitPyPattern(node)
  }

  open fun visitPyKeyValuePattern(node: PyAstKeyValuePattern) {
    visitPyPattern(node)
  }

  open fun visitPyMappingPattern(node: PyAstMappingPattern) {
    visitPyPattern(node)
  }

  open fun visitPyOrPattern(node: PyAstOrPattern) {
    visitPyPattern(node)
  }

  open fun visitPySequencePattern(node: PyAstSequencePattern) {
    visitPyPattern(node)
  }

  open fun visitPyValuePattern(node: PyAstValuePattern) {
    visitPyPattern(node)
  }

  open fun visitPyKeywordPattern(node: PyAstKeywordPattern) {
    visitPyPattern(node)
  }

  open fun visitPyLiteralPattern(node: PyAstLiteralPattern) {
    visitPyPattern(node)
  }

  open fun visitPyPatternArgumentList(node: PyAstPatternArgumentList) {
    visitPyElement(node)
  }

  open fun visitPyMatchStatement(node: PyAstMatchStatement) {
    visitPyStatement(node)
  }

  open fun visitPyCaseClause(node: PyAstCaseClause) {
    visitPyElement(node)
  }

  open fun visitPyTypeAliasStatement(node: PyAstTypeAliasStatement) {
    visitPyStatement(node)
  }

  open fun visitPyTypeParameter(node: PyAstTypeParameter) {
    visitPyElement(node)
  }

  open fun visitPyTypeParameterList(node: PyAstTypeParameterList) {
    visitPyElement(node)
  }

  open fun visitPyKeyValueExpression(node: PyAstKeyValueExpression) {
    visitPyExpression(node)
  }
}
