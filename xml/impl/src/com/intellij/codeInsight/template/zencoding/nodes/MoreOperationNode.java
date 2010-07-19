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

import com.intellij.openapi.util.text.LineTokenizer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
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

  private static void addChildrenToAllLeafs(GenerationNode root, Collection<GenerationNode> children) {
    if (root.isLeaf()) {
      root.addChildren(children);
    }
    else {
      for (GenerationNode child : root.getChildren()) {
        addChildrenToAllLeafs(child, children);
      }
    }
  }

  @NotNull
  @Override
  public List<GenerationNode> expand(int numberInIteration, String surroundedText) {
    if (myLeftOperand instanceof MulOperationNode || (myLeftOperand instanceof UnaryMulOperationNode && surroundedText != null)) {
      List<GenerationNode> result = new ArrayList<GenerationNode>();
      if (myLeftOperand instanceof MulOperationNode) {
        MulOperationNode mul = (MulOperationNode)myLeftOperand;
        for (int i = 0; i < mul.getRightOperand(); i++) {
          List<GenerationNode> parentNodes = mul.getLeftOperand().expand(i, surroundedText);
          List<GenerationNode> innerNodes = myRightOperand.expand(i, surroundedText);
          for (GenerationNode parentNode : parentNodes) {
            addChildrenToAllLeafs(parentNode, innerNodes);
          }
          result.addAll(parentNodes);
        }
      }
      else {
        UnaryMulOperationNode unaryMul = (UnaryMulOperationNode)myLeftOperand;
        String[] lines = LineTokenizer.tokenize(surroundedText, false);
        for (int i = 0; i < lines.length; i++) {
          List<GenerationNode> parentNodes = unaryMul.getOperand().expand(i, lines[i]);
          List<GenerationNode> innerNodes = myRightOperand.expand(i, lines[i]);
          for (GenerationNode parentNode : parentNodes) {
            addChildrenToAllLeafs(parentNode, innerNodes);
          }
          result.addAll(parentNodes);
        }
      }
      return result;
    }
    List<GenerationNode> leftGenNodes = myLeftOperand.expand(numberInIteration, surroundedText);
    for (GenerationNode leftGenNode : leftGenNodes) {
      List<GenerationNode> rightGenNodes = myRightOperand.expand(numberInIteration, surroundedText);
      addChildrenToAllLeafs(leftGenNode, rightGenNodes);
    }
    return leftGenNodes;
  }
}
