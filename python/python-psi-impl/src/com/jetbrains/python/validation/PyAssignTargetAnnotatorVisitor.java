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

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.PyPsiBundle.message;


public class PyAssignTargetAnnotatorVisitor extends PyElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  public PyAssignTargetAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  private @NotNull PyAnnotationHolder getHolder() {
    return myHolder;
  }

  private enum Operation {
    Assign, AugAssign, Delete, Except, For, With
  }

  @Override
  public void visitPyAssignmentStatement(final @NotNull PyAssignmentStatement node) {
    for (PyExpression expression : node.getRawTargets()) {
      expression.accept(new ExprVisitor(Operation.Assign));
    }

    PyExpression expression = node.getAssignedValue();
    if (expression instanceof PyAssignmentExpression) {
      getHolder()
        .newAnnotation(HighlightSeverity.ERROR, message("ANN.unparenthesized.assignment.expression.value"))
        .range(expression)
        .create();
    }
  }

  @Override
  public void visitPyAugAssignmentStatement(final @NotNull PyAugAssignmentStatement node) {
    node.getTarget().accept(new ExprVisitor(Operation.AugAssign));
  }

  @Override
  public void visitPyDelStatement(final @NotNull PyDelStatement node) {
    ExprVisitor visitor = new ExprVisitor(Operation.Delete);
    for (PyExpression expr : node.getTargets()) {
      expr.accept(visitor);
    }
  }

  @Override
  public void visitPyExceptBlock(final @NotNull PyExceptPart node) {
    PyExpression target = node.getTarget();
    if (target != null) {
      target.accept(new ExprVisitor(Operation.Except));
    }
  }

  @Override
  public void visitPyForStatement(final @NotNull PyForStatement node) {
    PyExpression target = node.getForPart().getTarget();
    if (target != null) {
      target.accept(new ExprVisitor(Operation.For));
      checkNotAssignmentExpression(target, message("ANN.assignment.expression.as.a.target"));
    }
  }

  @Override
  public void visitPyWithItem(@NotNull PyWithItem node) {
    PyExpression target = node.getTarget();
    if (target != null) {
      target.accept(new ExprVisitor(Operation.With));
    }
  }

  @Override
  public void visitPyExpressionStatement(@NotNull PyExpressionStatement node) {
    PyExpression expression = node.getExpression();
    if (expression instanceof PyAssignmentExpression) {
      getHolder()
        .newAnnotation(HighlightSeverity.ERROR, message("ANN.unparenthesized.assignment.expression.statement"))
        .range(expression)
        .create();
    }
  }

  @Override
  public void visitPyAssignmentExpression(@NotNull PyAssignmentExpression node) {
    final PyComprehensionElement comprehensionElement = PsiTreeUtil.getParentOfType(node, PyComprehensionElement.class, true, ScopeOwner.class);
    if (ScopeUtil.getScopeOwner(comprehensionElement) instanceof PyClass) {
      getHolder().newAnnotation(HighlightSeverity.ERROR,
                                message("ANN.assignment.expressions.within.a.comprehension.cannot.be.used.in.a.class.body")).create();
    }
  }

  @Override
  public void visitPyComprehensionElement(@NotNull PyComprehensionElement node) {
    final String targetMessage = message("ANN.assignment.expression.as.a.target");
    final String iterableMessage = message("ANN.assignment.expression.in.an.iterable");

    node.getForComponents().forEach(
      it -> {
        PyExpression iteratorVariable = it.getIteratorVariable();
        iteratorVariable.accept(new ExprVisitor(Operation.For));
        checkNotAssignmentExpression(iteratorVariable, targetMessage);
        checkNoAssignmentExpressionAsChild(it.getIteratedList(), iterableMessage);
      }
    );
  }

  private void checkNoAssignmentExpressionAsChild(@Nullable PyExpression expression, @NotNull @InspectionMessage String message) {
    PsiTreeUtil
      .findChildrenOfType(expression, PyAssignmentExpression.class)
      .forEach(it -> checkNotAssignmentExpression(it, message));
  }

  private void checkNotAssignmentExpression(@Nullable PyExpression expression, @NotNull @InspectionMessage String message) {
    if (PyPsiUtils.flattenParens(expression) instanceof PyAssignmentExpression) {
      getHolder()
        .newAnnotation(HighlightSeverity.ERROR, message)
        .range(expression)
        .create();
    }
  }

  private class ExprVisitor extends PyElementVisitor {
    private final Operation myOp;

    ExprVisitor(Operation op) {
      myOp = op;
    }

    @Override
    public void visitPyReferenceExpression(final @NotNull PyReferenceExpression node) {
      String referencedName = node.getReferencedName();
      if (PyNames.NONE.equals(referencedName)) {
        //noinspection DialogTitleCapitalization
        getHolder().newAnnotation(HighlightSeverity.ERROR, (myOp == Operation.Delete) ?
                                                           message("ANN.deleting.none") :
                                                           message("ANN.assign.to.none")).range(node).create();
      }
    }

    @Override
    public void visitPyTargetExpression(final @NotNull PyTargetExpression node) {
      String targetName = node.getName();
      if (PyNames.NONE.equals(targetName)) {
        final VirtualFile vfile = node.getContainingFile().getVirtualFile();
        if (vfile != null && !vfile.getUrl().contains("/" + PythonSdkUtil.SKELETON_DIR_NAME + "/")) {
          //noinspection DialogTitleCapitalization
          getHolder().newAnnotation(HighlightSeverity.ERROR,
                                    (myOp == Operation.Delete) ?
                                    message("ANN.deleting.none") :
                                    message("ANN.assign.to.none")).range(node)
            .create();
        }
      }
      if (PyNames.DEBUG.equals(targetName)) {
        if (LanguageLevel.forElement(node).isPy3K()) {
          getHolder().newAnnotation(HighlightSeverity.ERROR, message("ANN.assignment.to.keyword")).range(node).create();
        }
        else {
          getHolder().newAnnotation(HighlightSeverity.ERROR, message("ANN.cannot.assign.to.debug")).range(node).create();
        }
      }
    }

    @Override
    public void visitPyCallExpression(final @NotNull PyCallExpression node) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, (myOp == Operation.Delete) ?
                                                         message("ANN.cant.delete.call") :
                                                         message("ANN.cant.assign.to.call")).range(node)
        .create();
    }

    @Override
    public void visitPyGeneratorExpression(final @NotNull PyGeneratorExpression node) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, message(
        myOp == Operation.AugAssign ? "ANN.cant.aug.assign.to.generator" : "ANN.cant.assign.to.generator")).range(node).create();
    }

    @Override
    public void visitPyBinaryExpression(final @NotNull PyBinaryExpression node) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, message("ANN.cant.assign.to.operator")).range(node).create();
    }

    @Override
    public void visitPyTupleExpression(final @NotNull PyTupleExpression node) {
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
    public void visitPyParenthesizedExpression(final @NotNull PyParenthesizedExpression node) {
      if (myOp == Operation.AugAssign) {
        getHolder().newAnnotation(HighlightSeverity.ERROR, message("ANN.cant.aug.assign.to.tuple.or.generator")).range(node).create();
      }
      else {
        node.acceptChildren(this);
      }
    }

    @Override
    public void visitPyListLiteralExpression(final @NotNull PyListLiteralExpression node) {
      if (myOp == Operation.AugAssign) {
        getHolder().newAnnotation(HighlightSeverity.ERROR, message("ANN.cant.aug.assign.to.list.or.comprh")).range(node).create();
      }
      else {
        node.acceptChildren(this);
      }
    }

    @Override
    public void visitPyListCompExpression(final @NotNull PyListCompExpression node) {
      getHolder().markError(node, message(myOp == Operation.AugAssign ? "ANN.cant.aug.assign.to.comprh" : "ANN.cant.assign.to.comprh"));
    }

    @Override
    public void visitPyDictCompExpression(@NotNull PyDictCompExpression node) {
      getHolder().markError(node, message(myOp == Operation.AugAssign ? "ANN.cant.aug.assign.to.dict.comprh" : "ANN.cant.assign.to.dict.comprh"));
    }

    @Override
    public void visitPySetCompExpression(@NotNull PySetCompExpression node) {
      getHolder().markError(node, message(myOp == Operation.AugAssign ? "ANN.cant.aug.assign.to.set.comprh" : "ANN.cant.assign.to.set.comprh"));
    }

    @Override
    public void visitPyStarExpression(@NotNull PyStarExpression node) {
      super.visitPyStarExpression(node);
      if (!(node.getParent() instanceof PySequenceExpression)) {
        getHolder().markError(node, message("ANN.cant.aug.assign.starred.assignment.target.must.be.in.list.or.tuple"));
      }
    }

    @Override
    public void visitPyDictLiteralExpression(@NotNull PyDictLiteralExpression node) {
      checkLiteral(node);
    }

    @Override
    public void visitPySetLiteralExpression(@NotNull PySetLiteralExpression node) {
      checkLiteral(node);
    }

    @Override
    public void visitPyNumericLiteralExpression(final @NotNull PyNumericLiteralExpression node) {
      checkLiteral(node);
    }

    @Override
    public void visitPyStringLiteralExpression(final @NotNull PyStringLiteralExpression node) {
      checkLiteral(node);
    }

    private void checkLiteral(@NotNull PsiElement node) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, message(myOp == Operation.Delete ? "ANN.cant.delete.literal" : "ANN.cant.assign.to.literal")).range(node).create();
    }

    @Override
    public void visitPyLambdaExpression(final @NotNull PyLambdaExpression node) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, message("ANN.cant.assign.to.lambda")).range(node).create();
    }

    @Override
    public void visitPyNoneLiteralExpression(@NotNull PyNoneLiteralExpression node) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, message("ANN.assignment.to.keyword")).range(node).create();
    }

    @Override
    public void visitPyBoolLiteralExpression(@NotNull PyBoolLiteralExpression node) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, message("ANN.assignment.to.keyword")).range(node).create();
    }

    @Override
    public void visitPyPrefixExpression(@NotNull PyPrefixExpression node) {
      if (node.getOperator() == PyTokenTypes.AWAIT_KEYWORD) {
        getHolder().newAnnotation(HighlightSeverity.ERROR, message("ANN.cant.assign.to.await.expr")).range(node).create();
      }
    }
  }
}
