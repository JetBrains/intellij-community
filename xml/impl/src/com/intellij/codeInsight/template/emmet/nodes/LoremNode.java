// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.generators.LoremGenerator;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class LoremNode extends ZenCodingNode {
  private final int myWordsCount;
  private final LoremGenerator myLoremGenerator;

  public LoremNode(int wordsCount) {
    myLoremGenerator = new LoremGenerator();
    myWordsCount = wordsCount;
  }

  @Override
  public @NotNull List<GenerationNode> expand(int numberInIteration,
                                              int totalIterations, String surroundedText,
                                              CustomTemplateCallback callback,
                                              boolean insertSurroundedTextAtTheEnd, GenerationNode parent) {


    final TemplateToken templateToken = new TemplateToken("");
    final TemplateImpl template = new TemplateImpl("", myLoremGenerator.generate(myWordsCount, numberInIteration <= 0), "");
    templateToken.setTemplate(template, callback);
    final GenerationNode node = new GenerationNode(templateToken, numberInIteration,
                                                   totalIterations, surroundedText, insertSurroundedTextAtTheEnd, parent);
    return Collections.singletonList(node);
  }

  @Override
  public int getApproximateOutputLength(@Nullable CustomTemplateCallback callback) {
    
    return myWordsCount * 7;
  }

  @Override
  public String toString() {
    return "Lorem(" + myWordsCount + ")";
  }
}
