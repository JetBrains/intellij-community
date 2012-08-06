package com.jetbrains.python.psi.types;

import java.util.Collection;

/**
 *
 */
public class PyWeakUnionType extends PyUnionType implements PyWeakType{
  private PyWeakUnionType(Collection<PyType> members) {
    super(members);
  }
}
