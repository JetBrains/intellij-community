package com.jetbrains.python.debugger;

import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.impl.PyWeakTypeFactory;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParser;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author traff
 */
public class PyCallSignatureTypeProvider extends PyTypeProviderBase {
  public PyType getParameterType(final PyNamedParameter param, final PyFunction func, TypeEvalContext context) {
    String type = ((PySignatureCacheManagerImpl)PySignatureCacheManager.getInstance(param.getProject())).findParameterType(param);
    if (type != null) {
      PyType typeByName = PyTypeParser.getTypeByName(param, type);
      return buildWeakType(typeByName);
    }
    else {
      return null;
    }
  }

  private static PyType buildWeakType(PyType type) {
    return PyWeakTypeFactory.create(type);
  }
}
