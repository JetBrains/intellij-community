package com.jetbrains.python.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.resolve.QualifiedNameResolver;
import com.jetbrains.python.psi.resolve.QualifiedNameResolverImpl;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
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

  @Override
  public PyClassType createClassType(PyClass pyClass, boolean isDefinition) {
    return new PyClassTypeImpl(pyClass, isDefinition);
  }

  @Nullable
  @Override
  public String findShortestImportableName(PsiElement importer, VirtualFile targetFile) {
    return ResolveImportUtil.findShortestImportableName(importer, targetFile);
  }
}
