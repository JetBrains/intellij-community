// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstFStringFragmentFormatPart;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PyFStringFragmentFormatPart extends PyAstFStringFragmentFormatPart, PyElement {
  @Override
  @NotNull
  default List<PyFStringFragment> getFragments() {
    //noinspection unchecked
    return (List<PyFStringFragment>)PyAstFStringFragmentFormatPart.super.getFragments();
  }
}
