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

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.patterns.SyntaxMatchers;

import java.util.List;

/**
 * Highlights incorrect return statements: 'return' and 'yield' outside functions, returning values from generators;
 */
public class ReturnAnnotator extends PyAnnotator {
  public void visitPyReturnStatement(final PyReturnStatement node) {
    List<? extends PsiElement> found = SyntaxMatchers.IN_FUNCTION.search(node);
    if (found == null) {
      getHolder().createErrorAnnotation(node, "'return' outside of function");
      return;
    }
    PyFunction function = (PyFunction)found.get(0);
    if (node.getExpression() != null) {
      YieldVisitor visitor = new YieldVisitor();
      function.acceptChildren(visitor);
      if (visitor.haveYield()) {
        getHolder().createErrorAnnotation(node, "'return' with argument inside generator");
      }
    }

  }

  public void visitPyYieldExpression(final PyYieldExpression node) {
    if (SyntaxMatchers.IN_FUNCTION.search(node) == null) {
      getHolder().createErrorAnnotation(node, "'yield' outside of function");
    }
    /* this is now allowed in python 2.5
    if (node.getContainingElement(PyTryFinallyStatement.class) != null) {
      getHolder().createErrorAnnotation(node, "'yield' not allowed in a 'try' block with a 'finally' clause");
    }
    */
  }


  private static class YieldVisitor extends PyElementVisitor {
    private boolean _haveYield = false;

    public boolean haveYield() {
      return _haveYield;
    }

    @Override
    public void visitPyYieldExpression(final PyYieldExpression node) {
      _haveYield = true;
    }

    @Override
    public void visitPyElement(final PyElement node) {
      if (!_haveYield) {
        node.acceptChildren(this);
      }
    }

    @Override
    public void visitPyFunction(final PyFunction node) {
      // do not go to nested functions
    }
  }
}
