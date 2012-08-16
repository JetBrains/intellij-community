package com.jetbrains.python.debugger;

import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.impl.PyWeakTypeFactory;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParser;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class PyCallSignatureTypeProvider extends PyTypeProviderBase {
  public PyType getParameterType(final PyNamedParameter param, final PyFunction func, TypeEvalContext context) {
    final String name = param.getName();
    if (name != null) {
      final String type = ((PySignatureCacheManagerImpl)PySignatureCacheManager.getInstance(param.getProject())).findParameterType(func, name);
      if (type != null) {
        final PyType typeByName = PyTypeParser.getTypeByName(param, type);
        return buildWeakType(typeByName);
      }
    }
    return null;
  }

  @Nullable
  private static PyType buildWeakType(PyType type) {
    return PyWeakTypeFactory.create(type);
  }
}
