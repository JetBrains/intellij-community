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

import com.google.common.base.Joiner;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class TemplateNode extends ZenCodingNode {
  private static final Joiner JOINER = Joiner.on(",");
  private final TemplateToken myTemplateToken;

  public TemplateNode(TemplateToken templateToken) {
    myTemplateToken = templateToken;
  }

  public TemplateToken getTemplateToken() {
    return myTemplateToken;
  }

  @NotNull
  @Override
  public List<GenerationNode> expand(int numberInIteration,
                                     String surroundedText,
                                     CustomTemplateCallback callback,
                                     boolean insertSurroundedTextAtTheEnd, GenerationNode parent) {
    GenerationNode node = new GenerationNode(myTemplateToken, numberInIteration, surroundedText,
                                             insertSurroundedTextAtTheEnd, parent);
    return Arrays.asList(node);
  }

  @Override
  public String toString() {

    String result = myTemplateToken.getKey();
    List<Pair<String,String>> attributes = myTemplateToken.getAttribute2Value();
    if(!attributes.isEmpty()) {
      result += "[" + JOINER.join(myTemplateToken.getAttribute2Value()) + "]";
    }
    return "Template(" + result + ")";
  }
}
