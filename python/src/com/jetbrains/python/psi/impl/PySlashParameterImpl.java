// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
@ApiStatus.AvailableSince("2019.2")
public class PySlashParameterImpl extends PyBaseElementImpl implements PySlashParameter {

  public PySlashParameterImpl(ASTNode node) {
    super(node);
  }

  @Override
  public PyNamedParameter getAsNamed() {
    return null;
  }

  @Override
  public PyTupleParameter getAsTuple() {
    return null;
  }

  @Override
  public PyExpression getDefaultValue() {
    return null;
  }

  @Override
  public boolean hasDefaultValue() {
    return false;
  }

  @Nullable
  @Override
  public String getDefaultValueText() {
    return null;
  }

  @Override
  public boolean isSelf() {
    return false;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPySlashParameter(this);
  }
}
