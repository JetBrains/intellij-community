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
package com.intellij.codeInsight.template.emmet.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class MoreOperationNode extends ZenCodingNode {
  private final ZenCodingNode myLeftOperand;
  private final ZenCodingNode myRightOperand;

  public MoreOperationNode(ZenCodingNode leftOperand, ZenCodingNode rightOperand) {
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
  public List<ZenCodingNode> getChildren() {
    return ContainerUtil.newLinkedList(myLeftOperand, myRightOperand);
  }

  @NotNull
  @Override
  public List<GenerationNode> expand(int numberInIteration,
                                     int totalIterations, String surroundedText,
                                     CustomTemplateCallback callback,
                                     boolean insertSurroundedTextAtTheEnd, GenerationNode parent) {
    if (myLeftOperand instanceof MulOperationNode || (myLeftOperand instanceof UnaryMulOperationNode && surroundedText != null)) {
      List<GenerationNode> result = new ArrayList<GenerationNode>();
      if (myLeftOperand instanceof MulOperationNode) {
        MulOperationNode mul = (MulOperationNode)myLeftOperand;
        for (int i = 0; i < mul.getRightOperand(); i++) {
          List<GenerationNode> parentNodes = mul.getLeftOperand().expand(i, totalIterations, surroundedText, callback, insertSurroundedTextAtTheEnd,
                                                                         parent);
          for (GenerationNode parentNode : parentNodes) {
            myRightOperand.expand(i, totalIterations, surroundedText, callback, insertSurroundedTextAtTheEnd, parentNode);
          }
          result.addAll(parentNodes);
        }
      }
      else {
        UnaryMulOperationNode unaryMul = (UnaryMulOperationNode)myLeftOperand;
        String[] lines = LineTokenizer.tokenize(surroundedText, false);
        for (int i = 0; i < lines.length; i++) {
          String line = lines[i].trim();
          List<GenerationNode> parentNodes = unaryMul.getOperand().expand(i, totalIterations, line, callback, insertSurroundedTextAtTheEnd, parent);
          for (GenerationNode parentNode : parentNodes) {
            myRightOperand.expand(i, totalIterations, line, callback, insertSurroundedTextAtTheEnd, parentNode);
          }
          result.addAll(parentNodes);
        }
      }
      return result;
    }
    List<GenerationNode> leftGenNodes = myLeftOperand.expand(numberInIteration, totalIterations, surroundedText, callback, insertSurroundedTextAtTheEnd,
                                                             parent);
    for (GenerationNode leftGenNode : leftGenNodes) {
      myRightOperand.expand(numberInIteration,totalIterations , surroundedText, callback, insertSurroundedTextAtTheEnd, leftGenNode);
    }
    return leftGenNodes;
  }

  @Override
  public String toString() {
    return ">";
  }
}
