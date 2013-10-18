package com.jetbrains.python.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.QualifiedNameResolver;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author yole
 */
public abstract class PyPsiFacade {
  public static PyPsiFacade getInstance(Project project) {
    return ServiceManager.getService(project, PyPsiFacade.class);
  }

  public abstract QualifiedNameResolver qualifiedNameResolver(String qNameString);
  public abstract QualifiedNameResolver qualifiedNameResolver(QualifiedName qualifiedName);

  @Nullable
  public abstract PyClass findClass(String qName);

  @NotNull
  public abstract PyClassType createClassType(@NotNull PyClass pyClass, boolean isDefinition);

  @Nullable
  public abstract PyType createUnionType(@NotNull Collection<PyType> members);

  @Nullable
  public abstract PyType createTupleType(@NotNull Collection<PyType> members, @NotNull PsiElement anchor);

  @Nullable
  public abstract PyType parseTypeAnnotation(@NotNull String annotation, @NotNull PsiElement anchor);

  @Nullable
  public abstract String findShortestImportableName(@NotNull VirtualFile targetFile, @NotNull PsiElement anchor);
}
