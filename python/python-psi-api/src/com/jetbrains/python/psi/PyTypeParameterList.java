// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstTypeParameterList;
import com.jetbrains.python.psi.stubs.PyTypeParameterListStub;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a list of generic Type Parameters.<br>
 * e.g. {@code def foo[T, U](x: T | U): ...}<br>
 * where {@code [T, U]} is the list of generic Type Parameters.<br>
 * For more information see <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
public interface PyTypeParameterList extends PyAstTypeParameterList, PyElement, StubBasedPsiElement<PyTypeParameterListStub> {

  @Override
  @NotNull
  List<PyTypeParameter> getTypeParameters();
}
