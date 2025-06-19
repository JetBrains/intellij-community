/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * Visitor for python-specific nodes.
 */
public class PyElementVisitor extends PsiElementVisitor {
  public void visitPyElement(@NotNull PyElement node) {
    visitElement(node);
  }

  public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
    visitPyExpression(node);
  }

  public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
    visitPyExpression(node);
  }

  public void visitPyCallExpression(@NotNull PyCallExpression node) {
    visitPyExpression(node);
  }

  public void visitPyDecoratorList(@NotNull PyDecoratorList node) {
    visitPyElement(node);
  }

  public void visitPyDecorator(@NotNull PyDecorator node) {
    visitPyElement(node);
  }

  public void visitPyComprehensionElement(@NotNull PyComprehensionElement node) {
    visitPyExpression(node);
  }

  public void visitPyGeneratorExpression(@NotNull PyGeneratorExpression node) {
    visitPyComprehensionElement(node);
  }

  public void visitPyBinaryExpression(@NotNull PyBinaryExpression node) {
    visitPyExpression(node);
  }

  public void visitPyPrefixExpression(@NotNull PyPrefixExpression node) {
    visitPyExpression(node);
  }

  public void visitPySequenceExpression(@NotNull PySequenceExpression node) {
    visitPyExpression(node);
  }

  public void visitPyTupleExpression(@NotNull PyTupleExpression node) {
    visitPySequenceExpression(node);
  }

  public void visitPyParenthesizedExpression(@NotNull PyParenthesizedExpression node) {
    visitPyExpression(node);
  }

  public void visitPyDictLiteralExpression(@NotNull PyDictLiteralExpression node) {
    visitPyExpression(node);
  }

  public void visitPyListLiteralExpression(@NotNull PyListLiteralExpression node) {
    visitPySequenceExpression(node);
  }

  public void visitPySetLiteralExpression(@NotNull PySetLiteralExpression node) {
    visitPySequenceExpression(node);
  }

  public void visitPyListCompExpression(@NotNull PyListCompExpression node) {
    visitPyComprehensionElement(node);
  }

  public void visitPyDictCompExpression(@NotNull PyDictCompExpression node) {
    visitPyComprehensionElement(node);
  }

  public void visitPySetCompExpression(@NotNull PySetCompExpression node) {
    visitPyComprehensionElement(node);
  }

  public void visitPyLambdaExpression(@NotNull PyLambdaExpression node) {
    visitPyExpression(node);
  }

  public void visitPyAssignmentStatement(@NotNull PyAssignmentStatement node) {
    visitPyStatement(node);
  }

  public void visitPyAugAssignmentStatement(@NotNull PyAugAssignmentStatement node) {
    visitPyStatement(node);
  }

  public void visitPyDelStatement(@NotNull PyDelStatement node) {
    visitPyStatement(node);
  }

  public void visitPyReturnStatement(@NotNull PyReturnStatement node) {
    visitPyStatement(node);
  }

  public void visitPyYieldExpression(@NotNull PyYieldExpression node) {
    visitPyExpression(node);
  }

  public void visitPyTryExceptStatement(@NotNull PyTryExceptStatement node) {
    visitPyStatement(node);
  }

  public void visitPyRaiseStatement(@NotNull PyRaiseStatement node) {
    visitPyStatement(node);
  }

  public void visitPyBreakStatement(@NotNull PyBreakStatement node) {
    visitPyStatement(node);
  }

  public void visitPyContinueStatement(@NotNull PyContinueStatement node) {
    visitPyStatement(node);
  }

  public void visitPyGlobalStatement(@NotNull PyGlobalStatement node) {
    visitPyStatement(node);
  }

  public void visitPyFromImportStatement(@NotNull PyFromImportStatement node) {
    visitPyStatement(node);
  }

  public void visitPyIfStatement(@NotNull PyIfStatement node) {
    visitPyStatement(node);
  }

  public void visitPyForStatement(@NotNull PyForStatement node) {
    visitPyStatement(node);
  }

  public void visitPyWhileStatement(@NotNull PyWhileStatement node) {
    visitPyStatement(node);
  }

  public void visitPyWithStatement(@NotNull PyWithStatement node) {
    visitPyStatement(node);
  }

  public void visitPyExpressionStatement(@NotNull PyExpressionStatement node) {
    visitPyStatement(node);
  }

  public void visitPyStatement(@NotNull PyStatement node) {
    visitPyElement(node);
  }

  public void visitPyExpression(@NotNull PyExpression node) {
    visitPyElement(node);
  }

  public void visitPyParameterList(@NotNull PyParameterList node) {
    visitPyElement(node);
  }

  public void visitPyParameter(@NotNull PyParameter node) {
    visitPyElement(node);
  }

  public void visitPyNamedParameter(@NotNull PyNamedParameter node) {
    visitPyParameter(node);
  }

  public void visitPyTupleParameter(@NotNull PyTupleParameter node) {
    visitPyParameter(node);
  }

  public void visitPyArgumentList(@NotNull PyArgumentList node) {
    visitPyElement(node);
  }

  public void visitPyStatementList(@NotNull PyStatementList node) {
    visitPyElement(node);
  }

  public void visitPyExceptBlock(@NotNull PyExceptPart node) {
    visitPyElement(node);
  }

  public void visitPyFunction(@NotNull PyFunction node) {
    visitPyStatement(node);
  }

  public void visitPyClass(@NotNull PyClass node) {
    visitPyStatement(node);
  }

  public void visitPyFile(@NotNull PyFile node) {
    visitPyElement(node);
  }

  public void visitPyStringLiteralExpression(@NotNull PyStringLiteralExpression node) {
    visitPyElement(node);
  }

  public void visitPyFormattedStringElement(@NotNull PyFormattedStringElement node) {
    visitPyElement(node);
  }

  public void visitPyFStringFragment(@NotNull PyFStringFragment node) {
    visitPyElement(node);
  }

  public void visitPyNumericLiteralExpression(@NotNull PyNumericLiteralExpression node) {
    visitPyElement(node);
  }

  public void visitPyPrintStatement(@NotNull PyPrintStatement node) {
    visitPyStatement(node);
  }

  public void visitPyImportStatement(@NotNull PyImportStatement node) {
    visitPyStatement(node);
  }

  public void visitPyReprExpression(@NotNull PyReprExpression node) {
    visitPyExpression(node);
  }

  public void visitPyNonlocalStatement(@NotNull PyNonlocalStatement node) {
    visitPyStatement(node);
  }

  public void visitPyStarExpression(@NotNull PyStarExpression node) {
    visitPyExpression(node);
  }

  public void visitPyDoubleStarExpression(@NotNull PyDoubleStarExpression node) {
    visitPyExpression(node);
  }

  public void visitPySubscriptionExpression(@NotNull PySubscriptionExpression node) {
    visitPyExpression(node);
  }

  public void visitPyImportElement(@NotNull PyImportElement node) {
    visitPyElement(node);
  }

  public void visitPyStarImportElement(@NotNull PyStarImportElement node) {
    visitPyElement(node);
  }

  public void visitPyConditionalStatementPart(@NotNull PyConditionalStatementPart node) {
    visitPyElement(node);
  }

  public void visitPyAssertStatement(@NotNull PyAssertStatement node) {
    visitPyStatement(node);
  }

  public void visitPyPassStatement(@NotNull PyPassStatement node) {
    visitPyStatement(node);
  }

  public void visitPyNoneLiteralExpression(@NotNull PyNoneLiteralExpression node) {
    visitPyElement(node);
  }

  public void visitPyEllipsisLiteralExpression(@NotNull PyEllipsisLiteralExpression node) {
    visitPyElement(node);
  }

  public void visitPyBoolLiteralExpression(@NotNull PyBoolLiteralExpression node) {
    visitPyElement(node);
  }

  public void visitPyConditionalExpression(@NotNull PyConditionalExpression node) {
    visitPyElement(node);
  }

  public void visitPyKeywordArgument(@NotNull PyKeywordArgument node) {
    visitPyElement(node);
  }

  public void visitPyWithItem(@NotNull PyWithItem node) {
    visitPyElement(node);
  }

  public void visitPyTypeDeclarationStatement(@NotNull PyTypeDeclarationStatement node) {
    visitPyStatement(node);
  }

  public void visitPyAnnotation(@NotNull PyAnnotation node) {
    visitPyElement(node);
  }

  public void visitPySlashParameter(@NotNull PySlashParameter node) {
    visitPyElement(node);
  }

  public void visitPySingleStarParameter(@NotNull PySingleStarParameter node) {
    visitPyElement(node);
  }

  public void visitPyAssignmentExpression(@NotNull PyAssignmentExpression node) {
    visitPyExpression(node);
  }

  public void visitPyPattern(@NotNull PyPattern node) {
    visitPyElement(node);
  }

  public void visitPyAsPattern(@NotNull PyAsPattern node) {
    visitPyPattern(node);
  }

  public void visitPyCapturePattern(@NotNull PyCapturePattern node) {
    visitPyPattern(node);
  }

  public void visitWildcardPattern(@NotNull PyWildcardPattern node) {
    visitPyPattern(node);
  }

  public void visitPyClassPattern(@NotNull PyClassPattern node) {
    visitPyPattern(node);
  }

  public void visitPyDoubleStarPattern(@NotNull PyDoubleStarPattern node) {
    visitPyPattern(node);
  }

  public void visitPySingleStarPattern(@NotNull PySingleStarPattern node) {
    visitPyPattern(node);
  }

  public void visitPyGroupPattern(@NotNull PyGroupPattern node) {
    visitPyPattern(node);
  }

  public void visitPyKeyValuePattern(@NotNull PyKeyValuePattern node) {
    visitPyPattern(node);
  }

  public void visitPyMappingPattern(@NotNull PyMappingPattern node) {
    visitPyPattern(node);
  }

  public void visitPyOrPattern(@NotNull PyOrPattern node) {
    visitPyPattern(node);
  }

  public void visitPySequencePattern(@NotNull PySequencePattern node) {
    visitPyPattern(node);
  }

  public void visitPyValuePattern(@NotNull PyValuePattern node) {
    visitPyPattern(node);
  }

  public void visitPyKeywordPattern(@NotNull PyKeywordPattern node) {
    visitPyPattern(node);
  }

  public void visitPyLiteralPattern(@NotNull PyLiteralPattern node) {
    visitPyPattern(node);
  }

  public void visitPyPatternArgumentList(@NotNull PyPatternArgumentList node) {
    visitPyElement(node);
  }

  public void visitPyMatchStatement(@NotNull PyMatchStatement node) {
    visitPyStatement(node);
  }

  public void visitPyCaseClause(@NotNull PyCaseClause node) {
    visitPyElement(node);
  }

  public void visitPyTypeAliasStatement(@NotNull PyTypeAliasStatement node) {
    visitPyStatement(node);
  }

  public void visitPyTypeParameter(@NotNull PyTypeParameter node) {
    visitPyElement(node);
  }

  public void visitPyTypeParameterList(@NotNull PyTypeParameterList node) {
    visitPyElement(node);
  }

  public void visitPyKeyValueExpression(@NotNull PyKeyValueExpression node) {
    visitPyExpression(node);
  }
}
