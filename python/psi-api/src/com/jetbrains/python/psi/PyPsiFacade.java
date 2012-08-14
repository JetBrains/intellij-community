package com.jetbrains.python.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.QualifiedNameResolver;
import com.jetbrains.python.psi.types.PyClassType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class PyPsiFacade {
  public static PyPsiFacade getInstance(Project project) {
    return ServiceManager.getService(project, PyPsiFacade.class);
  }

  public abstract QualifiedNameResolver qualifiedNameResolver(String qNameString);
  public abstract QualifiedNameResolver qualifiedNameResolver(PyQualifiedName qualifiedName);

  @Nullable
  public abstract PyClass findClass(String qName);

  public abstract PyClassType createClassType(@NotNull PyClass pyClass, boolean isDefinition);

  @Nullable
  public abstract String findShortestImportableName(PsiElement importer, VirtualFile targetFile);
}
