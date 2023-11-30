// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FilterNode extends ZenCodingNode {
  private final ZenCodingNode myNode;
  private final String myFilter;

  public FilterNode(ZenCodingNode node, String filter) {
    myNode = node;
    myFilter = filter;
  }

  public ZenCodingNode getNode() {
    return myNode;
  }

  public String getFilter() {
    return myFilter;
  }

  @Override
  public @NotNull List<GenerationNode> expand(int numberInIteration,
                                              int totalIterations, String surroundedText,
                                              CustomTemplateCallback callback,
                                              boolean insertSurroundedTextAtTheEnd, GenerationNode parent) {
    return myNode.expand(numberInIteration, totalIterations, surroundedText, callback, insertSurroundedTextAtTheEnd, parent);
  }

  @Override
  public int getApproximateOutputLength(@Nullable CustomTemplateCallback callback) {
    return myNode.getApproximateOutputLength(callback);
  }

  @Override
  public String toString() {
    return "Filter(" + myFilter + ")";
  }
}
