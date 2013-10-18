package com.jetbrains.python.debugger;

import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyCallSignatureTypeProvider extends PyTypeProviderBase {
  public PyType getParameterType(@NotNull final PyNamedParameter param, @NotNull final PyFunction func, @NotNull TypeEvalContext context) {
    final String name = param.getName();
    if (name != null) {
      final String typeName = PySignatureCacheManager.getInstance(param.getProject()).findParameterType(func, name);
      if (typeName != null) {
        final PyType type = PyTypeParser.getTypeByName(param, typeName);
        if (type != null) {
          return PyDynamicallyEvaluatedType.create(type);
        }
      }
    }
    return null;
  }
}
