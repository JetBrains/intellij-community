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

package com.jetbrains.python.validation;

import com.intellij.lang.annotation.Annotation;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyHighlighter;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 13.06.2005
 * Time: 22:42:04
 * To change this template use File | Settings | File Templates.
 */
public class DocStringAnnotator extends PyAnnotator {
  @Override
  public void visitPyExpressionStatement(final PyExpressionStatement node) {
    PsiElement parent = node.getParent();
    if (parent instanceof PyFileImpl && isFirstNotCommentChild(node, parent)) {
      annotateDocStringStmt(node);
    }
    else if (parent instanceof PyStatementList && isFirstNotCommentChild(node, parent)) {
      PsiElement stmtParent = parent.getParent();
      if (stmtParent instanceof PyFunction || stmtParent instanceof PyClass) {
        annotateDocStringStmt(node);
      }
    }
  }

  private static boolean isFirstNotCommentChild(final PsiElement node, final PsiElement parent) {
    PsiElement[] children = parent.getChildren();
    for(PsiElement child: children) {
      if (child == node) return true;
      if (!(child instanceof PsiComment) && !(child instanceof PsiWhiteSpace)) break;
    }
    return false;
  }

  private void annotateDocStringStmt(PyExpressionStatement stmt) {
    final PyStringLiteralExpression docString = statementAsDocString(stmt);
    if (docString != null) {
      Annotation ann = getHolder().createInfoAnnotation(docString, null);
      ann.setTextAttributes(PyHighlighter.PY_DOC_COMMENT);
    }
  }

  @Nullable
  public static PyStringLiteralExpression statementAsDocString(final PyExpressionStatement stmt) {
    PyExpression expr = stmt.getExpression();
    if (expr instanceof PyLiteralExpression) {
      PyLiteralExpression litExpr = (PyLiteralExpression)expr;
      PsiElement elt = litExpr.getFirstChild();
      if (elt != null && elt.getNode().getElementType() == PyTokenTypes.STRING_LITERAL) {
        return (PyStringLiteralExpression) litExpr;
      }
    }
    return null;
  }

  @Nullable
  public static String findDocString(final PyStatementList statementList) {
    final PyStatement[] statements = statementList.getStatements();
    if (statements.length == 0) return null;
    if (statements [0] instanceof PyExpressionStatement) {
      PyStringLiteralExpression expr = statementAsDocString((PyExpressionStatement) statements [0]);
      if (expr != null) return expr.getStringValue();
    }
    return null;
  }
}
