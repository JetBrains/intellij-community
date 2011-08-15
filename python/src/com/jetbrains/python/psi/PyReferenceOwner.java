package com.jetbrains.python.psi;

import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;

/**
 * @author yole
 */
public interface PyReferenceOwner extends PyElement {
  PsiPolyVariantReference getReference(PyResolveContext context);
}
