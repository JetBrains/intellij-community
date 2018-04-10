// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public abstract class PyTypeVisitorExt extends PyTypeVisitor {

  public void visitNoneType(@NotNull PyNoneType type) {
    visitType(type);
  }

  public void visitUnionType(@NotNull PyUnionType type) {
    visitType(type);
  }

  public void visitGenericType(@NotNull PyGenericType type) {
    visitType(type);
  }

  public void visitCollectionType(@NotNull PyCollectionType type) {
    visitClassType(type);
  }

  public void visitTupleType(@NotNull PyTupleType type) {
    visitCollectionType(type);
  }

  public void visitNamedTupleType(@NotNull PyNamedTupleType type) {
    visitTupleType(type);
  }

  public void visitStructuralType(@NotNull PyStructuralType type) {
    visitType(type);
  }

  public void visitImportedModuleType(@NotNull PyImportedModuleType type) {
    visitType(type);
  }

  public void visitModuleType(@NotNull PyModuleType type) {
    visitType(type);
  }
}
