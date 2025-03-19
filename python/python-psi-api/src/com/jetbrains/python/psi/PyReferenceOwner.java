// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.ast.PyAstReferenceOwner;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;


public interface PyReferenceOwner extends PyAstReferenceOwner, PyElement {
  @NotNull
  PsiPolyVariantReference getReference(@NotNull PyResolveContext context);
}
