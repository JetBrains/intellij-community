// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findNotNullChildByClass;

@ApiStatus.Experimental
public interface PyAstKeyValuePattern extends PyAstPattern {
  default @NotNull PyAstPattern getKeyPattern() {
    return findNotNullChildByClass(this, PyAstPattern.class);
  }

  default @Nullable PyAstPattern getValuePattern() {
    return ObjectUtils.tryCast(getLastChild(), PyAstPattern.class);
  }

  @Override
  default boolean isIrrefutable() {
    return false;
  }
}
