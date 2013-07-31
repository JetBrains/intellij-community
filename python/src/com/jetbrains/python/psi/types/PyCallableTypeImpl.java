package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Pair;
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
  @Nullable private final List<Pair<String, PyType>> myParameters;
  @Nullable private final PyType myReturnType;

  public PyCallableTypeImpl(@Nullable List<Pair<String, PyType>> parameters, @Nullable PyType returnType) {
    myParameters = parameters;
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
  public List<Pair<String, PyType>> getParameters(@NotNull TypeEvalContext context) {
    return myParameters;
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
                         myParameters != null ?
                         StringUtil.join(myParameters,
                                         new Function<Pair<String, PyType>, String>() {
                                           @Override
                                           public String fun(Pair<String, PyType> param) {
                                             if (param != null) {
                                               final StringBuilder builder = new StringBuilder();
                                               final String name = param.getFirst();
                                               final PyType type = param.getSecond();
                                               if (name != null) {
                                                 builder.append(name);
                                                 if (type != null) {
                                                   builder.append(": ");
                                                 }
                                               }
                                               builder.append(type != null ? type.getName() : PyNames.UNKNOWN_TYPE);
                                               return builder.toString();
                                             }
                                             return PyNames.UNKNOWN_TYPE;
                                           }
                                         },
                                         ", ") :
                         "...",
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
