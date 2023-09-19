// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zolotov
 */
public class ClimbUpOperationNode extends ZenCodingNode {
  private final ZenCodingNode myLeftOperand;
  private final ZenCodingNode myRightOperand;

  public ClimbUpOperationNode(ZenCodingNode leftOperand, ZenCodingNode rightOperand) {
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
  public @NotNull List<GenerationNode> expand(int numberInIteration,
                                              int totalIterations, String surroundedText,
                                              CustomTemplateCallback callback,
                                              boolean insertSurroundedTextAtTheEnd, GenerationNode parent) {
    List<GenerationNode> result = new ArrayList<>();
    result.addAll(myLeftOperand.expand(numberInIteration, totalIterations, surroundedText, callback, insertSurroundedTextAtTheEnd, parent));
    GenerationNode grandParent = parent != null ? parent.getParent() : null;
    if (grandParent != null) {
      myRightOperand.expand(numberInIteration, totalIterations, surroundedText, callback, insertSurroundedTextAtTheEnd, grandParent);
    }
    else {
      result.addAll(myRightOperand.expand(numberInIteration, totalIterations, surroundedText, callback, insertSurroundedTextAtTheEnd, parent));
    }
    return result;
  }

  @Override
  public @NotNull List<ZenCodingNode> getChildren() {
    return ContainerUtil.newLinkedList(myLeftOperand, myRightOperand);
  }

  @Override
  public String toString() {
    return "^";
  }

  @Override
  public int getApproximateOutputLength(CustomTemplateCallback callback) {
    return myLeftOperand.getApproximateOutputLength(callback) + myRightOperand.getApproximateOutputLength(callback);
  }
}
