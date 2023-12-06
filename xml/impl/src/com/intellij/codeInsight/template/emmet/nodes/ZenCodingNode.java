// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class ZenCodingNode {
  public abstract @NotNull List<GenerationNode> expand(int numberInIteration,
                                                       int totalIterations, String surroundedText,
                                                       CustomTemplateCallback callback,
                                                       boolean insertSurroundedTextAtTheEnd, GenerationNode parent);

  public @NotNull List<ZenCodingNode> getChildren() {
    return Collections.emptyList();
  }

  public abstract int getApproximateOutputLength(@Nullable CustomTemplateCallback callback);
}
