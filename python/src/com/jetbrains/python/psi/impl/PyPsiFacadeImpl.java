package com.jetbrains.python.psi.impl;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.resolve.QualifiedNameResolver;
import com.jetbrains.python.psi.resolve.QualifiedNameResolverImpl;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyPsiFacadeImpl extends PyPsiFacade {
  private final Project myProject;

  public PyPsiFacadeImpl(Project project) {
    myProject = project;
  }

  @Override
  public QualifiedNameResolver qualifiedNameResolver(String qNameString) {
    return new QualifiedNameResolverImpl(qNameString);
  }

  @Override
  public QualifiedNameResolver qualifiedNameResolver(PyQualifiedName qualifiedName) {
    return new QualifiedNameResolverImpl(qualifiedName);
  }

  @Nullable
  @Override
  public PyClass findClass(String qName) {
    return PyClassNameIndex.findClass(qName, myProject);
  }
}
