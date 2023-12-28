// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.findChildrenByClass;

@ApiStatus.Experimental
public interface PyAstPatternArgumentList extends PyAstElement {
  default @NotNull List<? extends PyAstPattern> getPatterns() {
    return Arrays.asList(findChildrenByClass(this, PyAstPattern.class));
  }
}
