// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AddOperationNode extends ZenCodingNode {
  private final ZenCodingNode myLeftOperand;
  private final ZenCodingNode myRightOperand;

  public AddOperationNode(@NotNull ZenCodingNode leftOperand, @NotNull ZenCodingNode rightOperand) {
    myLeftOperand = leftOperand;
    myRightOperand = rightOperand;
  }

  public ZenCodingNode getLeftOperand() {
    return myLeftOperand;
  }

  public ZenCodingNode getRightOperand() {
    return myRightOperand;
  }

  @Override
  public @NotNull List<ZenCodingNode> getChildren() {
    return ContainerUtil.newLinkedList(myLeftOperand, myRightOperand);
  }

  @Override
  public @NotNull List<GenerationNode> expand(int numberInIteration,
                                              int totalIterations, String surroundedText,
                                              CustomTemplateCallback callback,
                                              boolean insertSurroundedTextAtTheEnd, GenerationNode parent) {
    List<GenerationNode> result = new ArrayList<>();
    List<GenerationNode> leftNodes = myLeftOperand.expand(numberInIteration, totalIterations, surroundedText, callback, false, parent);
    result.addAll(leftNodes);
    result.addAll(myRightOperand.expand(numberInIteration, totalIterations, surroundedText, callback, insertSurroundedTextAtTheEnd, parent));
    return result;
  }

  @Override
  public String toString() {
    return "+";
  }

  @Override
  public int getApproximateOutputLength(CustomTemplateCallback callback) {
    return myLeftOperand.getApproximateOutputLength(callback) + myRightOperand.getApproximateOutputLength(callback);
  }
}
