// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.ZenCodingUtil;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TemplateNode extends ZenCodingNode {
  private final TemplateToken myTemplateToken;
  private final @Nullable ZenCodingGenerator myGenerator;

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

  @Override
  public @NotNull List<GenerationNode> expand(int numberInIteration,
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
      result += attributes;
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
