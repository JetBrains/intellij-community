// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.google.common.collect.Lists;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.ast.*;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;


public class PyAssignmentStatementImpl extends PyElementImpl implements PyAssignmentStatement {
  private volatile PyExpression @Nullable [] myTargets;

  public PyAssignmentStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyAssignmentStatement(this);
  }

  @Override
  public PyExpression @NotNull [] getTargets() {
    PyExpression[] result = myTargets;
    if (result == null) {
      myTargets = result = PyAstAssignmentStatement.calcTargets(this, false, PyExpression.EMPTY_ARRAY);
    }
    return result;
  }

  @Override
  public PyExpression @NotNull [] getRawTargets() {
    return PyAstAssignmentStatement.calcTargets(this, true, PyExpression.EMPTY_ARRAY);
  }

  @Nullable
  public PsiNamedElement getNamedElement(@NotNull final String the_name) {
    // performance: check simple case first
    PyExpression[] targets = getTargets();
    if (targets.length == 1 && targets[0] instanceof PyTargetExpression target) {
      return !target.isQualified() && the_name.equals(target.getName()) ? target : null;
    }
    return PyUtil.IterHelper.findName(getNamedElements(), the_name);
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myTargets = null;
  }
}
