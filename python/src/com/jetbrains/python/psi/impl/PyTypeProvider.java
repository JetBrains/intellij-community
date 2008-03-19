package com.jetbrains.python.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyTypeProvider {
  ExtensionPointName<PyTypeProvider> EP_NAME = ExtensionPointName.create("Pythonid.typeProvider");
  
  @Nullable
  PyType getReferenceType(PsiElement referenceTarget);

  @Nullable
  PyType getParameterType(PyParameter param, final PyFunction func);
}
