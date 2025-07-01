// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstClassPattern;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface PyClassPattern extends PyAstClassPattern, PyPattern, PyCaptureContext {
  Set<String> SPECIAL_BUILTINS = Set.of(
    "bool", "bytearray", "bytes", "dict", "float", "frozenset", "int", "list", "set", "str", "tuple");
  
  @Override
  default @NotNull PyReferenceExpression getClassNameReference() {
    return (PyReferenceExpression)PyAstClassPattern.super.getClassNameReference();
  }

  @Override
  default @NotNull PyPatternArgumentList getArgumentList() {
    return (PyPatternArgumentList)PyAstClassPattern.super.getArgumentList();
  }
}
