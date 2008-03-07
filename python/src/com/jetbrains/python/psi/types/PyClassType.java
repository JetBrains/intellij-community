package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyClass;

/**
 * @author yole
 */
public class PyClassType implements PyType {
  private PyClass myClass;

  public PyClassType(final PyClass aClass) {
    myClass = aClass;
  }

  public PyClass getPyClass() {
    return myClass;
  }
}
