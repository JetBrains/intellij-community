package com.jetbrains.python.psi;

import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyReferenceOwner extends PyElement {
  @Nullable
  PsiPolyVariantReference getReference(PyResolveContext context);
}
