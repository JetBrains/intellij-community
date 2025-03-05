// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstDecoratorList;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A list of function decorators.
 */
public interface PyDecoratorList extends PyAstDecoratorList, PyElement, StubBasedPsiElement<PyDecoratorListStub> {
  /**
   * @return decorators of function, in order of declaration (outermost first).
   */
  @Override
  PyDecorator @NotNull [] getDecorators();

  @Override
  default @Nullable PyDecorator findDecorator(String name) {
    return (PyDecorator)PyAstDecoratorList.super.findDecorator(name);
  }
}
