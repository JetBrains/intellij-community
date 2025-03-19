// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MulOperationNode extends ZenCodingNode {
  private final ZenCodingNode myLeftOperand;
  private final int myRightOperand;

  public MulOperationNode(ZenCodingNode leftOperand, int rightOperand) {
    myLeftOperand = leftOperand;
    myRightOperand = rightOperand;
  }

  public ZenCodingNode getLeftOperand() {
    return myLeftOperand;
  }

  public int getRightOperand() {
    return myRightOperand;
  }

  @Override
  public @NotNull List<GenerationNode> expand(int numberInIteration,
                                              int totalIterations, String surroundedText,
                                              CustomTemplateCallback callback,
                                              boolean insertSurroundedTextAtTheEnd, GenerationNode parent) {
    List<GenerationNode> result = new ArrayList<>();
    for (int i = 0; i < myRightOperand; i++) {
      result.addAll(myLeftOperand.expand(i, myRightOperand, surroundedText, callback, insertSurroundedTextAtTheEnd, parent));
    }
    return result;
  }

  @Override
  public int getApproximateOutputLength(@Nullable CustomTemplateCallback callback) {
    return myLeftOperand.getApproximateOutputLength(callback) * myRightOperand;
  }

  @Override
  public String toString() {
    return "*";
  }
}
