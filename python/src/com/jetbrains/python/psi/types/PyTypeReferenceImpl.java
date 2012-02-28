package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public abstract class PyTypeReferenceImpl implements PyTypeReference {
  @NotNull
  public List<? extends RatedResolveResult> resolveMember(String name,
                                                          PyExpression location,
                                                          AccessDirection direction,
                                                          PyResolveContext resolveContext) {
    return Collections.emptyList();
  }

  public Object[] getCompletionVariants(String completionPrefix, PyExpression location, ProcessingContext context) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public PyType resolve(@Nullable PsiElement context, TypeEvalContext typeEvalContext) {
    Set<PyTypeReferenceImpl> seen = new HashSet<PyTypeReferenceImpl>();
    seen.add(this);
    PyType resolved;
    PyTypeReferenceImpl current = this;
    while (true) {
      resolved = current.resolveStep(null, typeEvalContext);
      if (!(resolved instanceof PyTypeReferenceImpl)) break;
      current = (PyTypeReferenceImpl) resolved;
      if (seen.contains(current)) return null;
      seen.add(current);
    }
    return resolved;
  }

  @Nullable
  protected abstract PyType resolveStep(@Nullable PsiElement context, @NotNull TypeEvalContext typeEvalContext);
}
