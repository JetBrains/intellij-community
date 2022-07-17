// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;


public abstract class PyPsiFacade {
  public static PyPsiFacade getInstance(Project project) {
    return project.getService(PyPsiFacade.class);
  }

  @NotNull
  public abstract List<PsiElement> resolveQualifiedName(@NotNull QualifiedName name, @NotNull PyQualifiedNameResolveContext context);

  @NotNull
  public abstract PyQualifiedNameResolveContext createResolveContextFromFoothold(@NotNull PsiElement foothold);
  /**
   * @deprecated use {@link #createClassByQName(String, PsiElement)} or skeleton may be found
   */
  @Deprecated(forRemoval = true)
  @Nullable
  public abstract PyClass findClass(String qName);

  @NotNull
  public abstract PyClassType createClassType(@NotNull PyClass pyClass, boolean isDefinition);

  @Nullable
  public abstract PyType createUnionType(@NotNull Collection<PyType> members);

  @Nullable
  public abstract PyType createTupleType(@NotNull List<PyType> members, @NotNull PsiElement anchor);

  @Nullable
  public abstract PyType parseTypeAnnotation(@NotNull String annotation, @NotNull PsiElement anchor);

  /**
   * Retrieve a top-level class by its qualified name. The name provided is supposed to be <em>fully qualified absolute name</em>
   * of the class, neither relative to the containing file of the anchor element, nor dependent on its imports.
   * <p>
   * The only exception to the rule above are built-in classes as it's too cumbersome to explicitly specify "__builtin__" or "builtins"
   * prefix for them each time, and, overall, it's rather intuitive that these classes can be found solely by their short names.
   * <p>
   * The anchor element is needed only to detect the corresponding module and its SDK.
   *
   * @param qName  qualified name of the required class
   * @param anchor arbitrary element located in the same module/SDK as the required class
   */
  @Nullable
  public abstract PyClass createClassByQName(@NotNull String qName, @NotNull PsiElement anchor);

  @Nullable
  public abstract String findShortestImportableName(@NotNull VirtualFile targetFile, @NotNull PsiElement anchor);

  @NotNull
  public abstract LanguageLevel getLanguageLevel(@NotNull PsiElement element);
}
