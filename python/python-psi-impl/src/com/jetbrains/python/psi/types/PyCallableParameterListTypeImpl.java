package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyNames;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class PyCallableParameterListTypeImpl implements PyCallableParameterListType {
  private final List<PyCallableParameter> myParameters;

  public PyCallableParameterListTypeImpl(@NotNull List<PyCallableParameter> parameters) {
    myParameters = List.copyOf(parameters);
  }

  @Override
  public @NotNull List<PyCallableParameter> getParameters() {
    return myParameters;
  }

  @Override
  public @NotNull List<PyCallableParameter> getUnpackedParameters(@NotNull TypeEvalContext context) {
    return StreamEx.of(getParameters())
      .flatMap(param -> {
        // TODO Unpack *args: *tuple[T1, T2] into (__x1: T1, __x2: T2, /)
        if (param.isKeywordContainer()) {
          PyType paramType = param.getType(context);
          if (paramType instanceof PyUnpackedTypedDictType unpackedTypedDictType) {
            return StreamEx.of(unpackedTypedDictType.getUnpackedParameters());
          }
        }
        return StreamEx.of(param);
      }).toList();
  }

  @Override
  public @NotNull String getName() {
    final TypeEvalContext context = TypeEvalContext.codeInsightFallback(null);
    return String.format("[%s]",
                         StringUtil.join(myParameters, param -> {
                                           PyType type = param.getType(context);
                                           return type != null ? type.getName() : PyNames.ANY_TYPE;
                                         },
                                         ", "));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PyCallableParameterListTypeImpl type = (PyCallableParameterListTypeImpl)o;
    return Objects.equals(myParameters, type.myParameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myParameters);
  }

  @Override
  public String toString() {
    return "PyCallableParameterListType: " + getName();
  }

  @Override
  public <T> T acceptTypeVisitor(@NotNull PyTypeVisitor<T> visitor) {
    return visitor.visitPyCallableParameterListType(this);
  }
}
