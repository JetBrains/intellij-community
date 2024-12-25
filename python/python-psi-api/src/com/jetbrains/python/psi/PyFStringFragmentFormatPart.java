// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstFStringFragmentFormatPart;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PyFStringFragmentFormatPart extends PyAstFStringFragmentFormatPart, PyElement {
  @Override
  default @NotNull List<PyFStringFragment> getFragments() {
    //noinspection unchecked
    return (List<PyFStringFragment>)PyAstFStringFragmentFormatPart.super.getFragments();
  }
}
