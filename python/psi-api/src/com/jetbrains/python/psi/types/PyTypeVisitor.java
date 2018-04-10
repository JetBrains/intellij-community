// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public abstract class PyTypeVisitor {
  public void visitType(@NotNull PyType type) {}

  public void visitCallableType(@NotNull PyCallableType type) {
    visitType(type);
  }

  public void visitFunctionType(@NotNull PyFunctionType type) {
    visitCallableType(type);
  }

  public void visitClassLikeType(@NotNull PyClassLikeType type) {
    visitCallableType(type);
  }

  public void visitClassType(@NotNull PyClassType type) {
    visitClassLikeType(type);
  }
}
