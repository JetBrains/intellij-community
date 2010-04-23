package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyExpression;

/**
 * @author yole
 */
public interface PySubscriptableType extends PyType {
  PyType getElementType(PyExpression index);
}
