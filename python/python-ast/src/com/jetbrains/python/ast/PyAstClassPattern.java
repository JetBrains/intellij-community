// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

@ApiStatus.Experimental
public interface PyAstClassPattern extends PyAstPattern {
  default @NotNull PyAstReferenceExpression getClassNameReference() {
    return Objects.requireNonNull(findChildByClass(this, PyAstReferenceExpression.class));
  }

  default @NotNull PyAstPatternArgumentList getArgumentList() {
    return Objects.requireNonNull(findChildByClass(this, PyAstPatternArgumentList.class));
  }

  @Override
  default boolean isIrrefutable() {
    return false;
  }

  @Override
  default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
    pyVisitor.visitPyClassPattern(this);
  }
}
