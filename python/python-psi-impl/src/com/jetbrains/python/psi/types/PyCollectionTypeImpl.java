// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPsiFacade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Concrete {@link PyClassType} used for parameterized class types (e.g. {@code list[int]}). It behaves exactly like
 * {@link PyClassTypeImpl} but additionally implements the deprecated {@link PyCollectionType} marker, so that
 * {@code instanceof PyCollectionType} checks in third-party plugins keep working. The actual type arguments are stored
 * in {@link PyClassTypeImpl} and exposed via {@link PyClassType#getTypeArguments()}.
 */
@SuppressWarnings("deprecation")
public class PyCollectionTypeImpl extends PyClassTypeImpl implements PyCollectionType {
  public PyCollectionTypeImpl(@NotNull PyClass source, boolean isDefinition, @NotNull List<? extends PyType> elementTypes) {
    super(source, isDefinition, elementTypes);
  }

  @Override
  protected @NotNull PyClassTypeImpl createInstance(@NotNull PyClass source,
                                                    boolean isDefinition,
                                                    @NotNull List<? extends PyType> typeArguments) {
    return new PyCollectionTypeImpl(source, isDefinition, typeArguments);
  }

  public static @Nullable PyCollectionTypeImpl createTypeByQName(final @NotNull PsiElement anchor,
                                                                 final @NotNull String classQualifiedName,
                                                                 final boolean isDefinition,
                                                                 final @NotNull List<? extends PyType> elementTypes) {
    final PyClass pyClass = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(classQualifiedName, anchor);
    if (pyClass == null) {
      return null;
    }
    return new PyCollectionTypeImpl(pyClass, isDefinition, elementTypes);
  }
}
