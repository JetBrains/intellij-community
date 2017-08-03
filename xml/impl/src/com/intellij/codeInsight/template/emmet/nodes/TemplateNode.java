/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInsight.template.emmet.ZenCodingUtil;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TemplateNode extends ZenCodingNode {
  private final TemplateToken myTemplateToken;
  @Nullable private final ZenCodingGenerator myGenerator;

  public TemplateNode(TemplateToken templateToken) {
    this(templateToken, null);
  }

  public TemplateNode(TemplateToken token, @Nullable ZenCodingGenerator generator) {
    myTemplateToken = token;
    myGenerator = generator;
  }

  public TemplateToken getTemplateToken() {
    return myTemplateToken;
  }

  @NotNull
  @Override
  public List<GenerationNode> expand(int numberInIteration,
                                     int totalIterations, String surroundedText,
                                     CustomTemplateCallback callback,
                                     boolean insertSurroundedTextAtTheEnd, GenerationNode parent) {
    TemplateToken templateToken = myTemplateToken;
    String templateKey = templateToken.getKey();
    if (myGenerator != null && StringUtil.containsChar(templateKey, '$') && callback.findApplicableTemplate(templateKey) == null) {
      String newTemplateKey = ZenCodingUtil.replaceMarkers(templateKey, numberInIteration, totalIterations, surroundedText);
      TemplateToken newTemplateToken = new TemplateToken(newTemplateKey, templateToken.getAttributes(), templateToken.isForceSingleTag());
      TemplateImpl template = myGenerator.createTemplateByKey(newTemplateKey, newTemplateToken.isForceSingleTag());
      if (template != null) {
        template.setDeactivated(true);
        newTemplateToken.setTemplate(template, callback);
        templateToken = newTemplateToken;
      }
    }

    GenerationNode node = new GenerationNode(templateToken, numberInIteration, totalIterations,
                                             surroundedText, insertSurroundedTextAtTheEnd, parent);
    return Collections.singletonList(node);
  }

  @Override
  public String toString() {
    String result = myTemplateToken.getKey();
    Map<String, String> attributes = myTemplateToken.getAttributes();
    if (!attributes.isEmpty()) {
      result += ContainerUtil.toString(attributes);
    }
    return "Template(" + result + ")";
  }

  @Override
  public int getApproximateOutputLength(@Nullable CustomTemplateCallback callback) {
    TemplateImpl template = myTemplateToken.getTemplate();
    if (template != null) {
      int result = template.getTemplateText().length();
      for (Map.Entry<String, String> attribute : myTemplateToken.getAttributes().entrySet()) {
        result += attribute.getKey().length() + attribute.getValue().length() + 4; //plus space, eq, quotes
      }
      return result;
    }
    return 0;
  }
}
