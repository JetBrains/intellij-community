// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyImportResolver {
  ExtensionPointName<PyImportResolver> EP_NAME = ExtensionPointName.create("Pythonid.importResolver");

  @Nullable
  PsiElement resolveImportReference(@NotNull QualifiedName name, @NotNull PyQualifiedNameResolveContext context, boolean withRoots);
}
