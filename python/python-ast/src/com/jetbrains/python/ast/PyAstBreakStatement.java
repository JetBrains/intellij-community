// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.ast.impl.PyUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
public interface PyAstBreakStatement extends PyAstStatement {
  @Nullable
  default PyAstLoopStatement getLoopStatement() {
    return PyUtilCore.getCorrespondingLoop(this);
  }
}
