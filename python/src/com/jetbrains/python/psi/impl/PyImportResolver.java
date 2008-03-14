package com.jetbrains.python.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyImportResolver {
  ExtensionPointName<PyImportResolver> EP_NAME = ExtensionPointName.create("Pythonid.importResolver");

  @Nullable
  PsiElement resolveImportReference(PyReferenceExpression importRef, final PsiElement importFrom);
}
