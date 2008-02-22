/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi;

import com.intellij.psi.PsiElementVisitor;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 05.06.2005
 * Time: 13:45:21
 * To change this template use File | Settings | File Templates.
 */
public class PyElementVisitor extends PsiElementVisitor {
  public void visitPyElement(final PyElement node) {
    visitElement(node);
  }

  public void visitPyReferenceExpression(final PyReferenceExpression node) {
    visitPyExpression(node);
  }

  public void visitPyTargetExpression(final PyTargetExpression node) {
    visitPyExpression(node);
  }

  public void visitPyCallExpression(final PyCallExpression node) {
    visitPyExpression(node);
  }

  public void visitPyGeneratorExpression(final PyGeneratorExpression node) {
    visitPyExpression(node);
  }

  public void visitPyBinaryExpression(final PyBinaryExpression node) {
    visitPyExpression(node);
  }

  public void visitPyTupleExpression(final PyTupleExpression node) {
    visitPyExpression(node);
  }

  public void visitPyParenthesizedExpression(final PyParenthesizedExpression node) {
    visitPyExpression(node);
  }

  public void visitPyListLiteralExpression(final PyListLiteralExpression node) {
    visitPyExpression(node);
  }

  public void visitPyListCompExpression(final PyListCompExpression node) {
    visitPyExpression(node);
  }

  public void visitPyLambdaExpression(final PyLambdaExpression node) {
    visitPyExpression(node);
  }

  public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
    visitPyStatement(node);
  }

  public void visitPyAugAssignmentStatement(final PyAugAssignmentStatement node) {
    visitPyStatement(node);
  }

  public void visitPyDelStatement(final PyDelStatement node) {
    visitPyStatement(node);
  }

  public void visitPyReturnStatement(final PyReturnStatement node) {
    visitPyStatement(node);
  }

  public void visitPyYieldExpression(final PyYieldExpression node) {
    visitPyExpression(node);
  }

  public void visitPyTryExceptStatement(final PyTryExceptStatement node) {
    visitPyStatement(node);
  }

  public void visitPyBreakStatement(final PyBreakStatement node) {
    visitPyStatement(node);
  }

  public void visitPyContinueStatement(final PyContinueStatement node) {
    visitPyStatement(node);
  }

  public void visitPyGlobalStatement(final PyGlobalStatement node) {
    visitPyStatement(node);
  }

  public void visitPyFromImportStatement(final PyFromImportStatement node) {
    visitPyStatement(node);
  }

  public void visitPyIfStatement(final PyIfStatement node) {
    visitPyStatement(node);
  }

  public void visitPyForStatement(final PyForStatement node) {
    visitPyStatement(node);
  }

  public void visitPyWhileStatement(final PyWhileStatement node) {
    visitPyStatement(node);
  }

  public void visitPyWithStatement(final PyWithStatement node) {
    visitPyStatement(node);
  }

  public void visitPyExpressionStatement(final PyExpressionStatement node) {
    visitPyStatement(node);
  }

  public void visitPyStatement(final PyStatement node) {
    visitPyElement(node);
  }

  public void visitPyExpression(final PyExpression node) {
    visitPyElement(node);
  }

  public void visitPyParameterList(final PyParameterList node) {
    visitPyElement(node);
  }

  public void visitPyParameter(final PyParameter node) {
    visitPyElement(node);
  }

  public void visitPyArgumentList(final PyArgumentList node) {
    visitPyElement(node);
  }

  public void visitPyStatementList(final PyStatementList node) {
    visitPyElement(node);
  }

  public void visitPyExceptBlock(final PyExceptBlock node) {
    visitPyElement(node);
  }

  public void visitPyFunction(final PyFunction node) {
    visitPyElement(node);
  }

  public void visitPyClass(final PyClass node) {
    visitPyElement(node);
  }

  public void visitPyFile(final PyFile node) {
    visitPyElement(node);
  }

  public void visitPyStringLiteralExpression(final PyStringLiteralExpression node) {
    visitPyElement(node);
  }

  public void visitPyNumericLiteralExpression(final PyNumericLiteralExpression node) {
    visitPyElement(node);
  }
}
