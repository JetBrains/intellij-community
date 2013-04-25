package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiMethod;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyJavaMethodType implements PyCallableType {
  private final PsiMethod myMethod;

  public PyJavaMethodType(PsiMethod method) {
    myMethod = method;
  }

  @Override
  public boolean isCallable() {
    return true;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @Nullable PyQualifiedExpression callSite) {
    return PyJavaTypeProvider.asPyType(myMethod.getReturnType());
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          AccessDirection direction,
                                                          PyResolveContext resolveContext) {
    return Collections.emptyList();
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PyExpression location, ProcessingContext context) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Nullable
  @Override
  public String getName() {
    return "Java method(" + myMethod.getContainingClass().getQualifiedName() + "." + myMethod.getName() + ")";
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }
}
