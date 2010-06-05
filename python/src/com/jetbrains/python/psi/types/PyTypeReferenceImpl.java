package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class PyTypeReferenceImpl implements PyTypeReference {
  @NotNull
  public Maybe<PsiElement> resolveMember(String name, Context context) {
    return NOT_RESOLVED_YET;
  }

  public Object[] getCompletionVariants(PyQualifiedExpression referenceExpression, ProcessingContext context) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
