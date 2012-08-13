package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class PyWeakTypeFactory {
  @Nullable
  public static PyWeakType create(@Nullable PyType type) {
    if (type == null) {
      return null;
    }
    else if (type instanceof PyClassType) {
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
