// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.ZenCodingUtil;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.codeInsight.template.emmet.tokens.TextToken;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class TextNode extends ZenCodingNode {
  private final String myText;

  public TextNode(@NotNull TextToken textToken) {
    final String text = textToken.getText();
    myText = text.substring(1, text.length() - 1);
  }

  public String getText() {
    return myText;
  }

  @Override
  public @NotNull List<GenerationNode> expand(int numberInIteration,
                                              int totalIterations, String surroundedText,
                                              CustomTemplateCallback callback,
                                              boolean insertSurroundedTextAtTheEnd, GenerationNode parent) {
    final TemplateToken templateToken = new TemplateToken("");
    final boolean containsSurroundedTextMarker = ZenCodingUtil.containsSurroundedTextMarker(myText);

    final String text = ZenCodingUtil.replaceMarkers(myText.replace("${nl}", "\n"), numberInIteration, totalIterations, surroundedText);
    final TemplateImpl template = new TemplateImpl("", text, "");
    templateToken.setTemplate(template, callback);

    final GenerationNode node = new GenerationNode(templateToken, numberInIteration, totalIterations,
                                                   containsSurroundedTextMarker ? null : surroundedText,
                                                   insertSurroundedTextAtTheEnd, parent);
    return Collections.singletonList(node);
  }

  @Override
  public int getApproximateOutputLength(@Nullable CustomTemplateCallback callback) {
    return myText.length();
  }

  @Override
  public String toString() {
    return "Text(" + myText + ")";
  }
}
