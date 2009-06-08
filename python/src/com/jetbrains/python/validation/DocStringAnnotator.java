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
import com.jetbrains.python.PyHighlighter;
import com.jetbrains.python.PythonDosStringFinder;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStringLiteralExpression;

/**
 * Highlights doc strings in classes, functions, and files.
 */
public class DocStringAnnotator extends PyAnnotator {

  @Override
  public void visitPyFile(PyFile node) {
    annotateDocStringStmt(PythonDosStringFinder.find(node));
  }


  @Override
  public void visitPyFunction(PyFunction node) {
    annotateDocStringStmt(PythonDosStringFinder.find(node.getStatementList()));
  }

  @Override
  public void visitPyClass(PyClass node) {
    annotateDocStringStmt(PythonDosStringFinder.find(node.getStatementList()));
  }

  private void annotateDocStringStmt(PyStringLiteralExpression stmt) {
    if (stmt != null) {
      Annotation ann = getHolder().createInfoAnnotation(stmt, null);
      ann.setTextAttributes(PyHighlighter.PY_DOC_COMMENT);
    }
  }

  /*
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
  */
}
