// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public abstract @NotNull List<PsiElement> resolveQualifiedName(@NotNull QualifiedName name, @NotNull PyQualifiedNameResolveContext context);

  public abstract @NotNull PyQualifiedNameResolveContext createResolveContextFromFoothold(@NotNull PsiElement foothold);

  public abstract @NotNull PyClassType createClassType(@NotNull PyClass pyClass, boolean isDefinition);

  public abstract @Nullable PyType createUnionType(@NotNull Collection<PyType> members);

  public abstract @Nullable PyType createTupleType(@NotNull List<PyType> members, @NotNull PsiElement anchor);

  public abstract @Nullable PyType parseTypeAnnotation(@NotNull String annotation, @NotNull PsiElement anchor);

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
  public abstract @Nullable PyClass createClassByQName(@NotNull String qName, @NotNull PsiElement anchor);

  public abstract @Nullable String findShortestImportableName(@NotNull VirtualFile targetFile, @NotNull PsiElement anchor);

  /**
   * @deprecated Use {@link LanguageLevel#forElement(PsiElement)}
   */
  @Deprecated(forRemoval = true)
  public abstract @NotNull LanguageLevel getLanguageLevel(@NotNull PsiElement element);
}
