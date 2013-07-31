package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author vlan
 */
public class PyCallableTypeImpl implements PyCallableType {
  @NotNull private final List<PyType> myParameterTypes;
  @Nullable private final PyType myReturnType;

  public PyCallableTypeImpl(@NotNull List<PyType> parameterTypes, @Nullable PyType returnType) {
    myParameterTypes = parameterTypes;
    myReturnType = returnType;
  }

  @Override
  public boolean isCallable() {
    return true;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @Nullable PyQualifiedExpression callSite) {
    return myReturnType;
  }

  @Nullable
  @Override
  public List<PyType> getParameterTypes(@NotNull TypeEvalContext context) {
    return myParameterTypes;
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    return null;
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PyExpression location, ProcessingContext context) {
    return new Object[0];
  }

  @Nullable
  @Override
  public String getName() {
    return String.format("(%s) -> %s",
                         StringUtil.join(myParameterTypes,
                                         new Function<PyType, String>() {
                                           @Override
                                           public String fun(PyType type) {
                                             return type != null ? type.getName() : PyNames.UNKNOWN_TYPE;
                                           }
                                         },
                                         ", "),
                         myReturnType != null ? myReturnType.getName() : PyNames.UNKNOWN_TYPE);
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }
}
