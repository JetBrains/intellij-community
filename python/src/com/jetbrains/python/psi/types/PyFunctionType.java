package com.jetbrains.python.psi.types;

import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Type of a particular function that is represented as a {@link Callable} in the PSI tree.
 *
 * @author vlan
 */
public class PyFunctionType implements PyCallableType {
  @NotNull private final Callable myCallable;

  public PyFunctionType(@NotNull Callable callable) {
    myCallable = callable;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @Nullable PyQualifiedExpression callSite) {
    return myCallable.getReturnType(context, callSite);
  }

  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          AccessDirection direction,
                                                          PyResolveContext resolveContext) {
    return Collections.emptyList();
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PyExpression location, ProcessingContext context) {
    return new Object[0];
  }

  @Override
  public String getName() {
    return "function";
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }

  @NotNull
  public Callable getCallable() {
    return myCallable;
  }
}
