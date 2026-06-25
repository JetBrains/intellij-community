// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.UserDataHolder;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public interface PyClassType extends PyClassLikeType, UserDataHolder {
  @NotNull
  PyClass getPyClass();

  /**
   * Concrete type arguments of this class instance, e.g. {@code [int]} for {@code list[int]}.
   * Returns an empty list for non-parameterized class types.
   */
  @NotNull
  List<PyType> getTypeArguments();

  /**
   * @return whether this class type carries concrete type arguments, e.g. {@code true} for {@code list[int]}
   * and {@code false} for a bare {@code list}.
   */
  default boolean isParameterized() {
    return !getTypeArguments().isEmpty();
  }

  /**
   * The element type produced when iterating over an instance of this type, or {@code null} if it is not iterable.
   * For most parameterized collections it is the first type argument; for mappings it is the key type.
   */
  @ApiStatus.Internal
  @Nullable
  PyType getIteratedItemType();

  @Override
  default <T> T acceptTypeVisitor(@NotNull PyTypeVisitor<T> visitor) {
    return visitor.visitPyClassType(this);
  }

  @Override
  @NotNull PyClassType toInstance();

  @Override
  @NotNull PyClassType toClass();
}
