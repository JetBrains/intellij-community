/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.zencoding.nodes;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AddOperationNode extends ZenCodingNode {
  private final ZenCodingNode myLeftOperand;
  private final ZenCodingNode myRightOperand;

  public AddOperationNode(ZenCodingNode leftOperand, ZenCodingNode rightOperand) {
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
  public List<GenerationNode> expand(int numberInIteration, String surroundedText) {
    List<GenerationNode> result = new ArrayList<GenerationNode>();
    List<GenerationNode> leftNodes = myLeftOperand.expand(numberInIteration, null);
    result.addAll(leftNodes);
    result.addAll(myRightOperand.expand(numberInIteration, surroundedText));
    return result;
  }
}
