package com.jetbrains.python.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyImportResolver {
  ExtensionPointName<PyImportResolver> EP_NAME = ExtensionPointName.create("Pythonid.importResolver");

  @Nullable
  PsiElement resolveImportReference(@NotNull PyElement importElement, @NotNull PyQualifiedName importText,
                                    @Nullable PyQualifiedName importFrom);
}
