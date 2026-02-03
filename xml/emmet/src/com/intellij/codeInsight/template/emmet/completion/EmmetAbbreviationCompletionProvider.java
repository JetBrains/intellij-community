// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.emmet.ZenCodingTemplate;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class EmmetAbbreviationCompletionProvider extends CompletionProvider<CompletionParameters> {
  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                @NotNull ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    if (LiveTemplateCompletionContributor.shouldShowAllTemplates() || !parameters.isAutoPopup()) {
       // covered with com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
      return;
    }

    ZenCodingTemplate zenCodingTemplate = CustomLiveTemplate.EP_NAME.findExtension(ZenCodingTemplate.class);
    if (zenCodingTemplate != null) {
      zenCodingTemplate.addCompletions(parameters, result);
    }
  }
}
