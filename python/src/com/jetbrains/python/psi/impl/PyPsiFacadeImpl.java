package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.resolve.QualifiedNameResolver;
import com.jetbrains.python.psi.resolve.QualifiedNameResolverImpl;

/**
 * @author yole
 */
public class PyPsiFacadeImpl extends PyPsiFacade {
  @Override
  public QualifiedNameResolver qualifiedNameResolver(String qNameString) {
    return new QualifiedNameResolverImpl(qNameString);
  }

  @Override
  public QualifiedNameResolver qualifiedNameResolver(PyQualifiedName qualifiedName) {
    return new QualifiedNameResolverImpl(qualifiedName);
  }
}
