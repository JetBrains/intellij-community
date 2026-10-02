// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.stubs.PyAnnotationStub;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public class PyAnnotationImpl extends PyBaseElementImpl<PyAnnotationStub> implements PyAnnotation {
  public PyAnnotationImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyAnnotationImpl(PyAnnotationStub stub) {
    super(stub, PyStubElementTypes.ANNOTATION);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyAnnotation(this);
  }
}
