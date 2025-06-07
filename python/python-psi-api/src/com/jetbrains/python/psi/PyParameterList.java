// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstParameterList;
import com.jetbrains.python.psi.stubs.PyParameterListStub;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents function parameter list.
 */
public interface PyParameterList extends PyAstParameterList, PyElement, StubBasedPsiElement<PyParameterListStub> {

  /**
   * Extracts the individual parameters.
   * Note that tuple parameters are flattened by this method.
   * @return a possibly empty array of named paramaters.
   */
  @Override
  PyParameter @NotNull [] getParameters();

  @Nullable
  PyNamedParameter findParameterByName(@NotNull String name);


  /**
   * Adds a paramter to list, after all other parameters.
   * @param param what to add
   */
  void addParameter(PyNamedParameter param);


  /**
   * @return true iff this list contains an '*args'-type parameter.
   */
  boolean hasPositionalContainer();

  /**
   * @return true iff this list contains a '**kwargs'-type parameter.
   */
  boolean hasKeywordContainer();

  /**
   * @param includeDefaultValue if true, include the default values after an "=".
   * @return representation of parameter list
   */
  default @NotNull String getPresentableText(boolean includeDefaultValue) {
    return getPresentableText(includeDefaultValue, null);
  }

  /**
   * @param includeDefaultValue if true, include the default values after an "=".
   * @param context             context to be used to resolve argument type
   * @return representation of parameter list
   * Also includes expected argument type for every parameter if {@code context} is not null and resolved type is not unknown.
   */
  @NotNull
  String getPresentableText(boolean includeDefaultValue, @Nullable TypeEvalContext context);

  @Nullable
  PyFunction getContainingFunction();
}
