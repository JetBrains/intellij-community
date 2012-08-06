package com.jetbrains.python.debugger;

import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.impl.PyWeakTypeFactory;
import com.jetbrains.python.psi.types.*;

/**
 * @author traff
 */
public class PyCallSignatureTypeProvider extends PyTypeProviderBase {
  public PyType getParameterType(final PyNamedParameter param, final PyFunction func, TypeEvalContext context) {
    if (!(param.getParent() instanceof PyParameterList)) return null;
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
