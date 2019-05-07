/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.template.emmet.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.newArrayList;

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

  @NotNull
  @Override
  public List<GenerationNode> expand(int numberInIteration,
                                     int totalIterations, String surroundedText,
                                     CustomTemplateCallback callback,
                                     boolean insertSurroundedTextAtTheEnd, GenerationNode parent) {
    List<GenerationNode> result = newArrayList();
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

  @NotNull
  @Override
  public List<ZenCodingNode> getChildren() {
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
