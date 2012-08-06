package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.types.*;

/**
 * @author traff
 */
public class PyWeakTypeFactory {
  public static PyWeakType create(PyType type) {
    if (type instanceof PyClassType) {
      PyClassType classType = (PyClassType)type;
      return new PyWeakClassType(classType.getPyClass(), classType.isDefinition());
    }
    else if (type instanceof PyNoneType) {
      return PyWeakNoneType.INSTANCE;
    }
    else {
      throw new IllegalStateException("For type " + type.getName());
    }
  }
}
