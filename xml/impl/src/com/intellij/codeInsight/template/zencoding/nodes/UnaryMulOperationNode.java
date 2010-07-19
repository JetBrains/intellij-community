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
  public List<GenerationNode> expand(int numberInIteration, String surroundedText) {
    if (surroundedText == null) {
      return myOperand.expand(numberInIteration, surroundedText);
    }
    String[] lines = LineTokenizer.tokenize(surroundedText, false);
    List<GenerationNode> result = new ArrayList<GenerationNode>();
    for (int i = 0; i < lines.length; i++) {
      result.addAll(myOperand.expand(i, lines[i]));
    }
    return result;
  }
}
