// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.emmet.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class UnaryMulOperationNode extends ZenCodingNode {
  private final ZenCodingNode myOperand;

  public UnaryMulOperationNode(ZenCodingNode operand) {
    myOperand = operand;
  }

  public ZenCodingNode getOperand() {
    return myOperand;
  }

  @NotNull
  @Override
  public List<GenerationNode> expand(int numberInIteration,
                                     int totalIterations, String surroundedText,
                                     CustomTemplateCallback callback,
                                     boolean insertSurroundedTextAtTheEnd, GenerationNode parent) {
    if (surroundedText == null) {
      return myOperand.expand(numberInIteration, totalIterations, null, callback, insertSurroundedTextAtTheEnd, parent);
    }
    String[] lines = LineTokenizer.tokenize(StringUtil.trim(surroundedText), false);
    List<GenerationNode> result = new ArrayList<>();
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      result.addAll(myOperand.expand(i, lines.length, line, callback, insertSurroundedTextAtTheEnd, parent));
    }
    return result;
  }

  @Override
  public int getApproximateOutputLength(@Nullable CustomTemplateCallback callback) {
    return myOperand.getApproximateOutputLength(callback);
  }

  @Override
  public String toString() {
    return "*";
  }
}
