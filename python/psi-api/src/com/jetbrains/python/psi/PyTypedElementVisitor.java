/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.*;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import org.jetbrains.annotations.NotNull;

/**
 * Visitor for python-specific nodes.
 */
public class PyTypedElementVisitor<T> {
  public T visitPyElement(final PyElement node) {
    return visitElement(node);
  }

  public T visitPyReferenceExpression(final PyReferenceExpression node) {
    return visitPyExpression(node);
  }

  public T visitPyTargetExpression(final PyTargetExpression node) {
    return visitPyExpression(node);
  }

  public T visitPyCallExpression(final PyCallExpression node) {
    return visitPyExpression(node);
  }

  public T visitPyDecoratorList(final PyDecoratorList node) {
    return visitElement(node);
  }

  public T visitPyComprehensionElement(final PyComprehensionElement node) {
    return visitPyExpression(node);
  }

  public T visitPyGeneratorExpression(final PyGeneratorExpression node) {
    return visitPyComprehensionElement(node);
  }

  public T visitPyBinaryExpression(final PyBinaryExpression node) {
    return visitPyExpression(node);
  }

  public T visitPyPrefixExpression(final PyPrefixExpression node) {
    return visitPyExpression(node);
  }

  public T visitPySequenceExpression(final PySequenceExpression node) {
    return visitPyExpression(node);
  }

  public T visitPyTupleExpression(final PyTupleExpression node) {
    return visitPySequenceExpression(node);
  }

  public T visitPyParenthesizedExpression(final PyParenthesizedExpression node) {
    return visitPyExpression(node);
  }

  public T visitPyDictLiteralExpression(final PyDictLiteralExpression node) {
    return visitPyExpression(node);
  }

  public T visitPyListLiteralExpression(final PyListLiteralExpression node) {
    return visitPySequenceExpression(node);
  }

  public T visitPySetLiteralExpression(final PySetLiteralExpression node) {
    return visitPyExpression(node);
  }

  public T visitPyListCompExpression(final PyListCompExpression node) {
    return visitPyComprehensionElement(node);
  }

  public T visitPyDictCompExpression(final PyDictCompExpression node) {
    return visitPyComprehensionElement(node);
  }

  public T visitPySetCompExpression(final PySetCompExpression node) {
    return visitPyComprehensionElement(node);
  }

  public T visitPyLambdaExpression(final PyLambdaExpression node) {
    return visitPyExpression(node);
  }

  public T visitPyAssignmentStatement(final PyAssignmentStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyAugAssignmentStatement(final PyAugAssignmentStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyDelStatement(final PyDelStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyReturnStatement(final PyReturnStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyYieldExpression(final PyYieldExpression node) {
    return visitPyExpression(node);
  }

  public T visitPyTryExceptStatement(final PyTryExceptStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyRaiseStatement(final PyRaiseStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyBreakStatement(final PyBreakStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyContinueStatement(final PyContinueStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyGlobalStatement(final PyGlobalStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyFromImportStatement(final PyFromImportStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyIfStatement(final PyIfStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyForStatement(final PyForStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyWhileStatement(final PyWhileStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyWithStatement(final PyWithStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyExpressionStatement(final PyExpressionStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyStatement(final PyStatement node) {
    return visitPyElement(node);
  }

  public T visitPyExpression(final PyExpression node) {
    return visitPyElement(node);
  }

  public T visitPyParameterList(final PyParameterList node) {
    return visitPyElement(node);
  }

  public T visitPyParameter(final PyParameter node) {
    return visitPyElement(node);
  }

  public T visitPyNamedParameter(final PyNamedParameter node) {
    return visitPyParameter(node);
  }

  public T visitPyTupleParameter(final PyTupleParameter node) {
    return visitPyParameter(node);
  }

  public T visitPyArgumentList(final PyArgumentList node) {
    return visitPyElement(node);
  }

  public T visitPyStatementList(final PyStatementList node) {
    return visitPyElement(node);
  }

  public T visitPyExceptBlock(final PyExceptPart node) {
    return visitPyElement(node);
  }

  public T visitPyFunction(final PyFunction node) {
    return visitPyElement(node);
  }

  public T visitPyClass(final PyClass node) {
    return visitPyElement(node);
  }

  public T visitPyFile(final PyFile node) {
    return visitPyElement(node);
  }

  public T visitPyStringLiteralExpression(final PyStringLiteralExpression node) {
    return visitPyElement(node);
  }

  public T visitPyNumericLiteralExpression(final PyNumericLiteralExpression node) {
    return visitPyElement(node);
  }

  public T visitPyPrintStatement(final PyPrintStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyImportStatement(PyImportStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyReprExpression(PyReprExpression node) {
    return visitPyExpression(node);
  }

  public T visitPyNonlocalStatement(PyNonlocalStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyStarExpression(PyStarExpression node) {
    return visitPyExpression(node);
  }

  public T visitPyDoubleStarExpression(PyDoubleStarExpression node) {
    return visitPyExpression(node);
  }

  public T visitPySubscriptionExpression(PySubscriptionExpression node) {
    return visitPyExpression(node);
  }

  public T visitPyImportElement(PyImportElement node) {
    return visitPyElement(node);
  }

  public T visitPyStarImportElement(PyStarImportElement node) {
    return visitPyElement(node);
  }

  public T visitPyConditionalStatementPart(PyConditionalStatementPart node) {
    return visitPyElement(node);
  }

  public T visitPyAssertStatement(final PyAssertStatement node) {
    return visitPyElement(node);
  }

  public T visitPyNoneLiteralExpression(final PyNoneLiteralExpression node) {
    return visitPyElement(node);
  }

  public T visitPyBoolLiteralExpression(final PyBoolLiteralExpression node) {
    return visitPyElement(node);
  }

  public T visitPyConditionalExpression(PyConditionalExpression node) {
    return visitPyElement(node);
  }

  public T visitPyKeywordArgument(PyKeywordArgument node) {
    return visitPyElement(node);
  }

  public T visitPyWithItem(PyWithItem node) {
    return visitPyElement(node);
  }

  public T visitPyTypeDeclarationStatement(PyTypeDeclarationStatement node) {
    return visitPyStatement(node);
  }

  public T visitPyAnnotation(PyAnnotation node) {
    return visitPyElement(node);
  }

  // Typed methods of PsiElementVisitor
  public T visitElement(PsiElement element) {
    ProgressIndicatorProvider.checkCanceled();
    return null;
  }

  public T visitFile(PsiFile file) {
    return visitElement(file);
  }

  public T visitBinaryFile(PsiBinaryFile file){
    return visitFile(file);
  }

  public T visitPlainTextFile(PsiPlainTextFile file){
    return visitFile(file);
  }

  public T visitErrorElement(PsiErrorElement element) {
    return visitElement(element);
  }

  public T visitPlainText(PsiPlainText content) {
    return visitElement(content);
  }

  public T visitDirectory(PsiDirectory dir) {
    return visitElement(dir);
  }

  public T visitComment(PsiComment comment) {
    return visitElement(comment);
  }

  public T visitWhiteSpace(PsiWhiteSpace space) {
    return visitElement(space);
  }

  public T visitOuterLanguageElement(OuterLanguageElement element) {
    return visitElement(element);
  }

  @NotNull
  public final Delegate<T> asPlainVisitor() {
    return new Delegate<>(this);
  }

  public static class Delegate<T> extends PyElementVisitor {
    private final PyTypedElementVisitor<T> myDelegate;
    private T myResult;

    public Delegate(@NotNull PyTypedElementVisitor<T> delegate) {
      myDelegate = delegate;
    }

    @Override
    public void visitPyElement(PyElement node) {
      myResult = myDelegate.visitPyElement(node);
    }

    @Override
    public void visitPyReferenceExpression(PyReferenceExpression node) {
      myResult = myDelegate.visitPyReferenceExpression(node);
    }

    @Override
    public void visitPyTargetExpression(PyTargetExpression node) {
      myResult = myDelegate.visitPyTargetExpression(node);
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      myResult = myDelegate.visitPyCallExpression(node);
    }

    @Override
    public void visitPyDecoratorList(PyDecoratorList node) {
      myResult = myDelegate.visitPyDecoratorList(node);
    }

    @Override
    public void visitPyComprehensionElement(PyComprehensionElement node) {
      myResult = myDelegate.visitPyComprehensionElement(node);
    }

    @Override
    public void visitPyGeneratorExpression(PyGeneratorExpression node) {
      myResult = myDelegate.visitPyGeneratorExpression(node);
    }

    @Override
    public void visitPyBinaryExpression(PyBinaryExpression node) {
      myResult = myDelegate.visitPyBinaryExpression(node);
    }

    @Override
    public void visitPyPrefixExpression(PyPrefixExpression node) {
      myResult = myDelegate.visitPyPrefixExpression(node);
    }

    @Override
    public void visitPySequenceExpression(PySequenceExpression node) {
      myResult = myDelegate.visitPySequenceExpression(node);
    }

    @Override
    public void visitPyTupleExpression(PyTupleExpression node) {
      myResult = myDelegate.visitPyTupleExpression(node);
    }

    @Override
    public void visitPyParenthesizedExpression(PyParenthesizedExpression node) {
      myResult = myDelegate.visitPyParenthesizedExpression(node);
    }

    @Override
    public void visitPyDictLiteralExpression(PyDictLiteralExpression node) {
      myResult = myDelegate.visitPyDictLiteralExpression(node);
    }

    @Override
    public void visitPyListLiteralExpression(PyListLiteralExpression node) {
      myResult = myDelegate.visitPyListLiteralExpression(node);
    }

    @Override
    public void visitPySetLiteralExpression(PySetLiteralExpression node) {
      myResult = myDelegate.visitPySetLiteralExpression(node);
    }

    @Override
    public void visitPyListCompExpression(PyListCompExpression node) {
      myResult = myDelegate.visitPyListCompExpression(node);
    }

    @Override
    public void visitPyDictCompExpression(PyDictCompExpression node) {
      myResult = myDelegate.visitPyDictCompExpression(node);
    }

    @Override
    public void visitPySetCompExpression(PySetCompExpression node) {
      myResult = myDelegate.visitPySetCompExpression(node);
    }

    @Override
    public void visitPyLambdaExpression(PyLambdaExpression node) {
      myResult = myDelegate.visitPyLambdaExpression(node);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      myResult = myDelegate.visitPyAssignmentStatement(node);
    }

    @Override
    public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
      myResult = myDelegate.visitPyAugAssignmentStatement(node);
    }

    @Override
    public void visitPyDelStatement(PyDelStatement node) {
      myResult = myDelegate.visitPyDelStatement(node);
    }

    @Override
    public void visitPyReturnStatement(PyReturnStatement node) {
      myResult = myDelegate.visitPyReturnStatement(node);
    }

    @Override
    public void visitPyYieldExpression(PyYieldExpression node) {
      myResult = myDelegate.visitPyYieldExpression(node);
    }

    @Override
    public void visitPyTryExceptStatement(PyTryExceptStatement node) {
      myResult = myDelegate.visitPyTryExceptStatement(node);
    }

    @Override
    public void visitPyRaiseStatement(PyRaiseStatement node) {
      myResult = myDelegate.visitPyRaiseStatement(node);
    }

    @Override
    public void visitPyBreakStatement(PyBreakStatement node) {
      myResult = myDelegate.visitPyBreakStatement(node);
    }

    @Override
    public void visitPyContinueStatement(PyContinueStatement node) {
      myResult = myDelegate.visitPyContinueStatement(node);
    }

    @Override
    public void visitPyGlobalStatement(PyGlobalStatement node) {
      myResult = myDelegate.visitPyGlobalStatement(node);
    }

    @Override
    public void visitPyFromImportStatement(PyFromImportStatement node) {
      myResult = myDelegate.visitPyFromImportStatement(node);
    }

    @Override
    public void visitPyIfStatement(PyIfStatement node) {
      myResult = myDelegate.visitPyIfStatement(node);
    }

    @Override
    public void visitPyForStatement(PyForStatement node) {
      myResult = myDelegate.visitPyForStatement(node);
    }

    @Override
    public void visitPyWhileStatement(PyWhileStatement node) {
      myResult = myDelegate.visitPyWhileStatement(node);
    }

    @Override
    public void visitPyWithStatement(PyWithStatement node) {
      myResult = myDelegate.visitPyWithStatement(node);
    }

    @Override
    public void visitPyExpressionStatement(PyExpressionStatement node) {
      myResult = myDelegate.visitPyExpressionStatement(node);
    }

    @Override
    public void visitPyStatement(PyStatement node) {
      myResult = myDelegate.visitPyStatement(node);
    }

    @Override
    public void visitPyExpression(PyExpression node) {
      myResult = myDelegate.visitPyExpression(node);
    }

    @Override
    public void visitPyParameterList(PyParameterList node) {
      myResult = myDelegate.visitPyParameterList(node);
    }

    @Override
    public void visitPyParameter(PyParameter node) {
      myResult = myDelegate.visitPyParameter(node);
    }

    @Override
    public void visitPyNamedParameter(PyNamedParameter node) {
      myResult = myDelegate.visitPyNamedParameter(node);
    }

    @Override
    public void visitPyTupleParameter(PyTupleParameter node) {
      myResult = myDelegate.visitPyTupleParameter(node);
    }

    @Override
    public void visitPyArgumentList(PyArgumentList node) {
      myResult = myDelegate.visitPyArgumentList(node);
    }

    @Override
    public void visitPyStatementList(PyStatementList node) {
      myResult = myDelegate.visitPyStatementList(node);
    }

    @Override
    public void visitPyExceptBlock(PyExceptPart node) {
      myResult = myDelegate.visitPyExceptBlock(node);
    }

    @Override
    public void visitPyFunction(PyFunction node) {
      myResult = myDelegate.visitPyFunction(node);
    }

    @Override
    public void visitPyClass(PyClass node) {
      myResult = myDelegate.visitPyClass(node);
    }

    @Override
    public void visitPyFile(PyFile node) {
      myResult = myDelegate.visitPyFile(node);
    }

    @Override
    public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
      myResult = myDelegate.visitPyStringLiteralExpression(node);
    }

    @Override
    public void visitPyNumericLiteralExpression(PyNumericLiteralExpression node) {
      myResult = myDelegate.visitPyNumericLiteralExpression(node);
    }

    @Override
    public void visitPyPrintStatement(PyPrintStatement node) {
      myResult = myDelegate.visitPyPrintStatement(node);
    }

    @Override
    public void visitPyImportStatement(PyImportStatement node) {
      myResult = myDelegate.visitPyImportStatement(node);
    }

    @Override
    public void visitPyReprExpression(PyReprExpression node) {
      myResult = myDelegate.visitPyReprExpression(node);
    }

    @Override
    public void visitPyNonlocalStatement(PyNonlocalStatement node) {
      myResult = myDelegate.visitPyNonlocalStatement(node);
    }

    @Override
    public void visitPyStarExpression(PyStarExpression node) {
      myResult = myDelegate.visitPyStarExpression(node);
    }

    @Override
    public void visitPyDoubleStarExpression(PyDoubleStarExpression node) {
      myResult = myDelegate.visitPyDoubleStarExpression(node);
    }

    @Override
    public void visitPySubscriptionExpression(PySubscriptionExpression node) {
      myResult = myDelegate.visitPySubscriptionExpression(node);
    }

    @Override
    public void visitPyImportElement(PyImportElement node) {
      myResult = myDelegate.visitPyImportElement(node);
    }

    @Override
    public void visitPyStarImportElement(PyStarImportElement node) {
      myResult = myDelegate.visitPyStarImportElement(node);
    }

    @Override
    public void visitPyConditionalStatementPart(PyConditionalStatementPart node) {
      myResult = myDelegate.visitPyConditionalStatementPart(node);
    }

    @Override
    public void visitPyAssertStatement(PyAssertStatement node) {
      myResult = myDelegate.visitPyAssertStatement(node);
    }

    @Override
    public void visitPyNoneLiteralExpression(PyNoneLiteralExpression node) {
      myResult = myDelegate.visitPyNoneLiteralExpression(node);
    }

    @Override
    public void visitPyBoolLiteralExpression(PyBoolLiteralExpression node) {
      myResult = myDelegate.visitPyBoolLiteralExpression(node);
    }

    @Override
    public void visitPyConditionalExpression(PyConditionalExpression node) {
      myResult = myDelegate.visitPyConditionalExpression(node);
    }

    @Override
    public void visitPyKeywordArgument(PyKeywordArgument node) {
      myResult = myDelegate.visitPyKeywordArgument(node);
    }

    @Override
    public void visitPyWithItem(PyWithItem node) {
      myResult = myDelegate.visitPyWithItem(node);
    }

    @Override
    public void visitPyTypeDeclarationStatement(PyTypeDeclarationStatement node) {
      myResult = myDelegate.visitPyTypeDeclarationStatement(node);
    }

    @Override
    public void visitPyAnnotation(PyAnnotation node) {
      myResult = myDelegate.visitPyAnnotation(node);
    }

    public T getResult() {
      return myResult;
    }
  }

}
