// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.UserDataHolder;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;


public interface PyClassType extends PyClassLikeType, UserDataHolder {
  @NotNull
  PyClass getPyClass();

  @Override
  default <T> T acceptTypeVisitor(@NotNull PyTypeVisitor<T> visitor) {
    return visitor.visitPyClassType(this);
  }

  @Override
  @NotNull PyClassType toInstance();

  @Override
  @NotNull PyClassType toClass();
}
