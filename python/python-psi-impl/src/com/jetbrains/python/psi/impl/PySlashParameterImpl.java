// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.stubs.PySlashParameterStub;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class PySlashParameterImpl extends PyBaseElementImpl<PySlashParameterStub> implements PySlashParameter {

  public PySlashParameterImpl(ASTNode node) {
    super(node);
  }

  public PySlashParameterImpl(PySlashParameterStub stub) {
    super(stub, PyStubElementTypes.SLASH_PARAMETER);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPySlashParameter(this);
  }
}
