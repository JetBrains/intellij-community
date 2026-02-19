// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstAnnotation;
import com.jetbrains.python.psi.stubs.PyAnnotationStub;
import org.jetbrains.annotations.Nullable;


public interface PyAnnotation extends PyAstAnnotation, PyElement, StubBasedPsiElement<PyAnnotationStub> {
  @Override
  default @Nullable PyExpression getValue() {
    return (PyExpression)PyAstAnnotation.super.getValue();
  }
}
