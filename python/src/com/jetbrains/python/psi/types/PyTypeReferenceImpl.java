package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyQualifiedExpression;

/**
 * @author yole
 */
public abstract class PyTypeReferenceImpl implements PyTypeReference {
  public PsiElement resolveMember(String name) {
    return null;
  }

  public Object[] getCompletionVariants(PyQualifiedExpression referenceExpression, ProcessingContext context) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
