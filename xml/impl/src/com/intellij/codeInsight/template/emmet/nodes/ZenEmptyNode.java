// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class ZenEmptyNode extends ZenCodingNode {
  public static final ZenCodingNode INSTANCE = new ZenEmptyNode();

  @Override
  public @NotNull List<GenerationNode> expand(int numberInIteration,
                                              int totalIterations, String surroundedText,
                                              CustomTemplateCallback callback,
                                              boolean insertSurroundedTextAtTheEnd,
                                              GenerationNode parent) {
    return Collections.emptyList();
  }

  @Override
  public int getApproximateOutputLength(@Nullable CustomTemplateCallback callback) {
    return 0;
  }

  @Override
  public String toString() {
    return "EMPTY_NODE";
  }
}
