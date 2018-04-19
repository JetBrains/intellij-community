// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.emmet.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class MoreOperationNode extends ZenCodingNode {
  private final ZenCodingNode myLeftOperand;
  private final ZenCodingNode myRightOperand;

  public MoreOperationNode(@NotNull ZenCodingNode leftOperand, @NotNull ZenCodingNode rightOperand) {
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

  @Override
  public int getApproximateOutputLength(@Nullable CustomTemplateCallback callback) {
    int mul = myLeftOperand instanceof MulOperationNode ? ((MulOperationNode)myLeftOperand).getRightOperand() : 1;
    return myLeftOperand.getApproximateOutputLength(callback) + (myRightOperand.getApproximateOutputLength(callback) * mul);
  }

  @NotNull
  @Override
  public List<GenerationNode> expand(int numberInIteration,
                                     int totalIterations, String surroundedText,
                                     CustomTemplateCallback callback,
                                     boolean insertSurroundedTextAtTheEnd, GenerationNode parent) {
    if (myLeftOperand instanceof MulOperationNode || (myLeftOperand instanceof UnaryMulOperationNode && surroundedText != null)) {
      List<GenerationNode> result = new ArrayList<>();
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
        String[] lines = LineTokenizer.tokenize(StringUtil.trim(surroundedText), false);
        for (int i = 0; i < lines.length; i++) {
          String line = lines[i].trim();
          List<GenerationNode> parentNodes =
            unaryMul.getOperand().expand(i, totalIterations, line, callback, insertSurroundedTextAtTheEnd, parent);
          for (GenerationNode parentNode : parentNodes) {
            myRightOperand.expand(i, totalIterations, line, callback, insertSurroundedTextAtTheEnd, parentNode);
          }
          result.addAll(parentNodes);
        }
      }
      return result;
    }
    List<GenerationNode> leftGenNodes = myLeftOperand.expand(numberInIteration, totalIterations, surroundedText, callback, insertSurroundedTextAtTheEnd, parent);

    if (leftGenNodes.isEmpty()) {
      return myRightOperand.expand(numberInIteration, totalIterations, surroundedText, callback, insertSurroundedTextAtTheEnd, parent);
    }
    
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
