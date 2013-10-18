package com.jetbrains.python.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.QualifiedNameResolveContext;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyImportResolver {
  ExtensionPointName<PyImportResolver> EP_NAME = ExtensionPointName.create("Pythonid.importResolver");

  @Nullable
  PsiElement resolveImportReference(QualifiedName name, QualifiedNameResolveContext context);
}
