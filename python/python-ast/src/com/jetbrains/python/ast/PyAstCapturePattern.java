// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public interface PyAstCapturePattern extends PyAstPattern {
  @Override
  default boolean isIrrefutable() {
    return true;
  }
}
