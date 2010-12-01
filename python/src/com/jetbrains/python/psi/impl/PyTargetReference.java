package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyTargetReference extends PyReferenceImpl {
  public PyTargetReference(PyQualifiedExpression element, PyResolveContext context) {
    super(element, context);
  }

  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    final ResolveResult[] results = super.multiResolve(incompleteCode);
    if (results.length > 0) {
      return results;
    }
    // resolve to self if no other target found
    return new ResolveResult[] { new PsiElementResolveResult(myElement) };
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final PyImportElement importElement = PsiTreeUtil.getParentOfType(myElement, PyImportElement.class);
    // reference completion is useless in 'as' part of import statement (PY-2384)
    if (importElement != null && myElement == importElement.getAsNameElement()) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    return super.getVariants();
  }
}
