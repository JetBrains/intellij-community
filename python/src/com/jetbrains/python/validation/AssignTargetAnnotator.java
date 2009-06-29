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
import static com.jetbrains.python.PyBundle.message;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 10.06.2005
 * Time: 23:27:40
 * To change this template use File | Settings | File Templates.
 */
public class AssignTargetAnnotator extends PyAnnotator {
  private enum Operation {
    Assign, AugAssign, Delete, Except, For
  }

  @Override
  public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
    ExprVisitor visitor = new ExprVisitor(Operation.Assign);
    boolean found = false;
    for (PyElement expr : node.iterateNames()) {
      expr.accept(visitor);
      found = true;
    }
    if (! found) {
      PsiElement lhs = node.getFirstChild();
      if (lhs instanceof PyElement) {
        lhs.accept(visitor);
      }
    }
  }

  @Override
  public void visitPyAugAssignmentStatement(final PyAugAssignmentStatement node) {
    node.getTarget().accept(new ExprVisitor(Operation.AugAssign));
  }

  @Override
  public void visitPyDelStatement(final PyDelStatement node) {
    ExprVisitor visitor = new ExprVisitor(Operation.Delete);
    for (PyExpression expr : node.getTargets()) {
      expr.accept(visitor);
    }
  }

  @Override
  public void visitPyExceptBlock(final PyExceptPart node) {
    PyExpression target = node.getTarget();
    if (target != null) {
      target.accept(new ExprVisitor(Operation.Except));
    }
  }

  @Override
  public void visitPyForStatement(final PyForStatement node) {
    PyExpression target = node.getForPart().getTarget();
    if (target != null) {
      target.accept(new ExprVisitor(Operation.For));
    }
  }

  private class ExprVisitor extends PyElementVisitor {
    private final Operation _op;
    private final String DELETING_NONE = message("ANN.deleting.none");
    private final String ASSIGNMENT_TO_NONE = message("ANN.assign.to.none");
    private final String CANT_ASSIGN_TO_FUNCTION_CALL = message("ANN.cant.assign.to.call");
    private final String CANT_DELETE_FUNCTION_CALL = message("ANN.cant.delete.call");

    public ExprVisitor(Operation op) {
      _op = op;
    }

    @Override
    public void visitPyReferenceExpression(final PyReferenceExpression node) {
      String referencedName = node.getReferencedName();
      if (referencedName != null && referencedName.equals(PyNames.NONE)) {
        getHolder().createErrorAnnotation(node, (_op == Operation.Delete) ? DELETING_NONE : ASSIGNMENT_TO_NONE);
      }
    }

    @Override
    public void visitPyTargetExpression(final PyTargetExpression node) {
      String targetName = node.getName();
      if (targetName != null && targetName.equals(PyNames.NONE)) {
        if (node.getContainingFile() != PyBuiltinCache.getInstance(node.getProject()).getBuiltinsFile()){
          getHolder().createErrorAnnotation(node, (_op == Operation.Delete) ? DELETING_NONE : ASSIGNMENT_TO_NONE);
        }
      }
    }

    @Override
    public void visitPyCallExpression(final PyCallExpression node) {
      getHolder().createErrorAnnotation(node, (_op == Operation.Delete) ? CANT_DELETE_FUNCTION_CALL : CANT_ASSIGN_TO_FUNCTION_CALL);
    }

    @Override
    public void visitPyGeneratorExpression(final PyGeneratorExpression node) {
      getHolder().createErrorAnnotation(node, message(
        _op == Operation.AugAssign ? "ANN.cant.aug.assign.to.generator" : "ANN.cant.assign.to.generator"));
    }

    @Override
    public void visitPyBinaryExpression(final PyBinaryExpression node) {
      getHolder().createErrorAnnotation(node, message("ANN.cant.assign.to.operator"));
    }

    @Override
    public void visitPyTupleExpression(final PyTupleExpression node) {
      if (node.getElements().length == 0) {
        getHolder().createErrorAnnotation(node, message("ANN.cant.assign.to.parens"));
      }
      else if (_op == Operation.AugAssign) {
        getHolder().createErrorAnnotation(node, message("ANN.cant.aug.assign.to.tuple.or.generator"));
      }
      else {
        node.acceptChildren(this);
      }
    }

    @Override
    public void visitPyParenthesizedExpression(final PyParenthesizedExpression node) {
      if (_op == Operation.AugAssign) {
        getHolder().createErrorAnnotation(node, message("ANN.cant.aug.assign.to.tuple.or.generator"));
      }
      else {
        node.acceptChildren(this);
      }
    }

    @Override
    public void visitPyListLiteralExpression(final PyListLiteralExpression node) {
      if (node.getElements().length == 0) {
        getHolder().createErrorAnnotation(node, message("ANN.cant.assign.to.brackets"));
      }
      else if (_op == Operation.AugAssign) {
        getHolder().createErrorAnnotation(node, message("ANN.cant.aug.assign.to.list.or.comprh"));
      }
      else {
        node.acceptChildren(this);
      }
    }

    @Override
    public void visitPyListCompExpression(final PyListCompExpression node) {
      getHolder()
        .createErrorAnnotation(node, message(_op == Operation.AugAssign ? "ANN.cant.aug.assign.to.comprh" : "ANN.cant.assign.to.comprh"));
    }

    @Override
    public void visitPyDictLiteralExpression(PyDictLiteralExpression node) {
      checkLiteral(node);
    }

    public void visitPyNumericLiteralExpression(final PyNumericLiteralExpression node) {
      checkLiteral(node);
    }

    public void visitPyStringLiteralExpression(final PyStringLiteralExpression node) {
      checkLiteral(node);
    }

    private void checkLiteral(PyExpression node) {
      getHolder().createErrorAnnotation(node, message(_op == Operation.Delete? "ANN.cant.delete.literal" : "ANN.cant.assign.to.literal"));
    }

    public void visitPyLambdaExpression(final PyLambdaExpression node) {
      getHolder().createErrorAnnotation(node, message("ANN.cant.assign.to.lambda"));
    }
  }
}
