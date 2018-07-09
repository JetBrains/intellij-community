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
package com.jetbrains.python.validation;

import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.sdk.PythonSdkType;

import static com.jetbrains.python.PyBundle.message;

/**
 * @author yole
 */
public class AssignTargetAnnotator extends PyAnnotator {
  private enum Operation {
    Assign, AugAssign, Delete, Except, For, With
  }

  @Override
  public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
    for (PyExpression expression : node.getRawTargets()) {
      expression.accept(new ExprVisitor(Operation.Assign));
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

  @Override
  public void visitPyWithItem(PyWithItem node) {
    PyExpression target = node.getTarget();
    if (target != null) {
      target.accept(new ExprVisitor(Operation.With));
    }
  }

  private class ExprVisitor extends PyElementVisitor {
    private final Operation myOp;
    private final String DELETING_NONE = message("ANN.deleting.none");
    private final String ASSIGNMENT_TO_NONE = message("ANN.assign.to.none");
    private final String CANT_ASSIGN_TO_FUNCTION_CALL = message("ANN.cant.assign.to.call");
    private final String CANT_DELETE_FUNCTION_CALL = message("ANN.cant.delete.call");

    public ExprVisitor(Operation op) {
      myOp = op;
    }

    @Override
    public void visitPyReferenceExpression(final PyReferenceExpression node) {
      String referencedName = node.getReferencedName();
      if (PyNames.NONE.equals(referencedName)) {
        getHolder().createErrorAnnotation(node, (myOp == Operation.Delete) ? DELETING_NONE : ASSIGNMENT_TO_NONE);
      }
    }

    @Override
    public void visitPyTargetExpression(final PyTargetExpression node) {
      String targetName = node.getName();
      if (PyNames.NONE.equals(targetName)) {
        final VirtualFile vfile = node.getContainingFile().getVirtualFile();
        if (vfile != null && !vfile.getUrl().contains("/" + PythonSdkType.SKELETON_DIR_NAME + "/")){
          getHolder().createErrorAnnotation(node, (myOp == Operation.Delete) ? DELETING_NONE : ASSIGNMENT_TO_NONE);
        }
      }
      if (PyNames.DEBUG.equals(targetName)) {
        if (LanguageLevel.forElement(node).isPy3K()) {
          getHolder().createErrorAnnotation(node, "assignment to keyword");
        }
        else {
          getHolder().createErrorAnnotation(node, "cannot assign to __debug__");
        }
      }
    }

    @Override
    public void visitPyCallExpression(final PyCallExpression node) {
      getHolder().createErrorAnnotation(node, (myOp == Operation.Delete) ? CANT_DELETE_FUNCTION_CALL : CANT_ASSIGN_TO_FUNCTION_CALL);
    }

    @Override
    public void visitPyGeneratorExpression(final PyGeneratorExpression node) {
      getHolder().createErrorAnnotation(node, message(
        myOp == Operation.AugAssign ? "ANN.cant.aug.assign.to.generator" : "ANN.cant.assign.to.generator"));
    }

    @Override
    public void visitPyBinaryExpression(final PyBinaryExpression node) {
      getHolder().createErrorAnnotation(node, message("ANN.cant.assign.to.operator"));
    }

    @Override
    public void visitPyTupleExpression(final PyTupleExpression node) {
      if (node.isEmpty() && LanguageLevel.forElement(node).isPython2()) {
        getHolder().createErrorAnnotation(node, message("ANN.cant.assign.to.parens"));
      }
      else if (myOp == Operation.AugAssign) {
        getHolder().createErrorAnnotation(node, message("ANN.cant.aug.assign.to.tuple.or.generator"));
      }
      else {
        node.acceptChildren(this);
      }
    }

    @Override
    public void visitPyParenthesizedExpression(final PyParenthesizedExpression node) {
      if (myOp == Operation.AugAssign) {
        getHolder().createErrorAnnotation(node, message("ANN.cant.aug.assign.to.tuple.or.generator"));
      }
      else {
        node.acceptChildren(this);
      }
    }

    @Override
    public void visitPyListLiteralExpression(final PyListLiteralExpression node) {
      if (myOp == Operation.AugAssign) {
        getHolder().createErrorAnnotation(node, message("ANN.cant.aug.assign.to.list.or.comprh"));
      }
      else {
        node.acceptChildren(this);
      }
    }

    @Override
    public void visitPyListCompExpression(final PyListCompExpression node) {
      markError(node, message(myOp == Operation.AugAssign ? "ANN.cant.aug.assign.to.comprh" : "ANN.cant.assign.to.comprh"));
    }

    @Override
    public void visitPyDictCompExpression(PyDictCompExpression node) {
      markError(node, message(myOp == Operation.AugAssign ? "ANN.cant.aug.assign.to.dict.comprh" : "ANN.cant.assign.to.dict.comprh"));
    }

    @Override
    public void visitPySetCompExpression(PySetCompExpression node) {
      markError(node, message(myOp == Operation.AugAssign ? "ANN.cant.aug.assign.to.set.comprh" : "ANN.cant.assign.to.set.comprh"));
    }

    @Override
    public void visitPyStarExpression(PyStarExpression node) {
      super.visitPyStarExpression(node);
      if (!(node.getParent() instanceof PySequenceExpression)) {
        markError(node, "starred assignment target must be in a list or tuple");
      }
    }

    @Override
    public void visitPyDictLiteralExpression(PyDictLiteralExpression node) {
      checkLiteral(node);
    }

    @Override
    public void visitPySetLiteralExpression(PySetLiteralExpression node) {
      checkLiteral(node);
    }

    @Override
    public void visitPyNumericLiteralExpression(final PyNumericLiteralExpression node) {
      checkLiteral(node);
    }

    @Override
    public void visitPyStringLiteralExpression(final PyStringLiteralExpression node) {
      checkLiteral(node);
    }

    private void checkLiteral(PyExpression node) {
      getHolder().createErrorAnnotation(node, message(myOp == Operation.Delete? "ANN.cant.delete.literal" : "ANN.cant.assign.to.literal"));
    }

    @Override
    public void visitPyLambdaExpression(final PyLambdaExpression node) {
      getHolder().createErrorAnnotation(node, message("ANN.cant.assign.to.lambda"));
    }

    @Override
    public void visitPyNoneLiteralExpression(PyNoneLiteralExpression node) {
      getHolder().createErrorAnnotation(node, "assignment to keyword");
    }

    @Override
    public void visitPyBoolLiteralExpression(PyBoolLiteralExpression node) {
      getHolder().createErrorAnnotation(node, "assignment to keyword");
    }
  }
}
