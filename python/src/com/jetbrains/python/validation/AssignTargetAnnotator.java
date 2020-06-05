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

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.PyPsiBundle.message;

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

    errorOnUnparenthesizedAssignmentExpression(node.getAssignedValue(),
                                               "at the top level of the right hand side of an assignment statement");
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

  @Override
  public void visitPyExpressionStatement(PyExpressionStatement node) {
    errorOnUnparenthesizedAssignmentExpression(node.getExpression(), "at the top level of an expression statement");
  }

  @Override
  public void visitPyNamedParameter(PyNamedParameter node) {
    errorOnUnparenthesizedAssignmentExpression(node.getDefaultValue(), "at the top level of a function default value");
  }

  @Override
  public void visitPyKeywordArgument(PyKeywordArgument node) {
    errorOnUnparenthesizedAssignmentExpression(node.getValueExpression(), "for the value of a keyword argument in a call");
  }

  @Override
  public void visitPyLambdaExpression(PyLambdaExpression node) {
    errorOnUnparenthesizedAssignmentExpression(node.getBody(), "at the top level of a lambda function");
  }

  @Override
  public void visitPyAnnotation(PyAnnotation node) {
    errorOnUnparenthesizedAssignmentExpression(node.getValue(), "as annotations for arguments, return values and assignments");
  }

  @Override
  public void visitPyAssignmentExpression(PyAssignmentExpression node) {
    final PyComprehensionElement comprehensionElement = PsiTreeUtil.getParentOfType(node, PyComprehensionElement.class, true, ScopeOwner.class);
    if (ScopeUtil.getScopeOwner(comprehensionElement) instanceof PyClass) {
      getHolder().newAnnotation(HighlightSeverity.ERROR,
                                PyBundle.message("ANN.assignment.expressions.within.a.comprehension.cannot.be.used.in.a.class.body")).create();
    }
  }

  private void errorOnUnparenthesizedAssignmentExpression(@Nullable PyExpression expression, @NotNull String suffix) {
    if (expression instanceof PyAssignmentExpression) {
      getHolder().newAnnotation(HighlightSeverity.ERROR,
                                PyBundle.message("ANN.unparenthesized.assignment.expressions.are.prohibited.0", suffix)).range(expression).create();
    }
  }

  private class ExprVisitor extends PyElementVisitor {
    private final Operation myOp;
    private final String DELETING_NONE = message("ANN.deleting.none");
    private final String ASSIGNMENT_TO_NONE = message("ANN.assign.to.none");
    private final String CANT_ASSIGN_TO_FUNCTION_CALL = message("ANN.cant.assign.to.call");
    private final String CANT_DELETE_FUNCTION_CALL = message("ANN.cant.delete.call");

    ExprVisitor(Operation op) {
      myOp = op;
    }

    @Override
    public void visitPyReferenceExpression(final PyReferenceExpression node) {
      String referencedName = node.getReferencedName();
      if (PyNames.NONE.equals(referencedName)) {
        getHolder().newAnnotation(HighlightSeverity.ERROR, (myOp == Operation.Delete) ? DELETING_NONE : ASSIGNMENT_TO_NONE).range(node).create();
      }
    }

    @Override
    public void visitPyTargetExpression(final PyTargetExpression node) {
      String targetName = node.getName();
      if (PyNames.NONE.equals(targetName)) {
        final VirtualFile vfile = node.getContainingFile().getVirtualFile();
        if (vfile != null && !vfile.getUrl().contains("/" + PythonSdkUtil.SKELETON_DIR_NAME + "/")){
          getHolder().newAnnotation(HighlightSeverity.ERROR, (myOp == Operation.Delete) ? DELETING_NONE : ASSIGNMENT_TO_NONE).range(node).create();
        }
      }
      if (PyNames.DEBUG.equals(targetName)) {
        if (LanguageLevel.forElement(node).isPy3K()) {
          getHolder().newAnnotation(HighlightSeverity.ERROR, PyBundle.message("ANN.assignment.to.keyword")).range(node).create();
        }
        else {
          getHolder().newAnnotation(HighlightSeverity.ERROR, PyBundle.message("ANN.cannot.assign.to.debug")).range(node).create();
        }
      }
    }

    @Override
    public void visitPyCallExpression(final PyCallExpression node) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, (myOp == Operation.Delete) ? CANT_DELETE_FUNCTION_CALL : CANT_ASSIGN_TO_FUNCTION_CALL).range(node).create();
    }

    @Override
    public void visitPyGeneratorExpression(final PyGeneratorExpression node) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, message(
        myOp == Operation.AugAssign ? "ANN.cant.aug.assign.to.generator" : "ANN.cant.assign.to.generator")).range(node).create();
    }

    @Override
    public void visitPyBinaryExpression(final PyBinaryExpression node) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, message("ANN.cant.assign.to.operator")).range(node).create();
    }

    @Override
    public void visitPyTupleExpression(final PyTupleExpression node) {
      if (node.isEmpty() && LanguageLevel.forElement(node).isPython2()) {
        getHolder().newAnnotation(HighlightSeverity.ERROR, message("ANN.cant.assign.to.parens")).range(node).create();
      }
      else if (myOp == Operation.AugAssign) {
        getHolder().newAnnotation(HighlightSeverity.ERROR, message("ANN.cant.aug.assign.to.tuple.or.generator")).range(node).create();
      }
      else {
        node.acceptChildren(this);
      }
    }

    @Override
    public void visitPyParenthesizedExpression(final PyParenthesizedExpression node) {
      if (myOp == Operation.AugAssign) {
        getHolder().newAnnotation(HighlightSeverity.ERROR, message("ANN.cant.aug.assign.to.tuple.or.generator")).range(node).create();
      }
      else {
        node.acceptChildren(this);
      }
    }

    @Override
    public void visitPyListLiteralExpression(final PyListLiteralExpression node) {
      if (myOp == Operation.AugAssign) {
        getHolder().newAnnotation(HighlightSeverity.ERROR, message("ANN.cant.aug.assign.to.list.or.comprh")).range(node).create();
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

    private void checkLiteral(@NotNull PsiElement node) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, message(myOp == Operation.Delete ? "ANN.cant.delete.literal" : "ANN.cant.assign.to.literal")).range(node).create();
    }

    @Override
    public void visitPyLambdaExpression(final PyLambdaExpression node) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, message("ANN.cant.assign.to.lambda")).range(node).create();
    }

    @Override
    public void visitPyNoneLiteralExpression(PyNoneLiteralExpression node) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, PyBundle.message("ANN.assignment.to.keyword")).range(node).create();
    }

    @Override
    public void visitPyBoolLiteralExpression(PyBoolLiteralExpression node) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, PyBundle.message("ANN.assignment.to.keyword")).range(node).create();
    }
  }
}
