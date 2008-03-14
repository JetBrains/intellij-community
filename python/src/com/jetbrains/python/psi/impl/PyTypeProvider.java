package com.jetbrains.python.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyTypeProvider {
  ExtensionPointName<PyTypeProvider> EP_NAME = ExtensionPointName.create("Pythonid.typeProvider");
  
  @Nullable
  PyType getReferenceType(PsiElement referenceTarget);
}
