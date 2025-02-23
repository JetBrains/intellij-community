// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.UserDataHolder;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;


public interface PyClassType extends PyClassLikeType, UserDataHolder {
  @NotNull
  PyClass getPyClass();

  /**
   * @param name    name to check
   * @param context type evaluation context
   * @return true if attribute with the specified name could be created or updated.
   * Does not take `typing.Final` into account.
   * @see PyClass#getSlots(TypeEvalContext)
   */
  default boolean isAttributeWritable(@NotNull String name, @NotNull TypeEvalContext context) {
    return true;
  }

  @Override
  default <T> T acceptTypeVisitor(@NotNull PyTypeVisitor<T> visitor) {
    return visitor.visitPyClassType(this);
  }
}
