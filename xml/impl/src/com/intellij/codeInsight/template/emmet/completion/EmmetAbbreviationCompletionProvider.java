/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.emmet.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.emmet.ZenCodingTemplate;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * User: zolotov
 * Date: 8/9/13
 */
public class EmmetAbbreviationCompletionProvider extends CompletionProvider<CompletionParameters> {
  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    if (LiveTemplateCompletionContributor.shouldShowAllTemplates() || !parameters.isAutoPopup()) {
      /**
       * covered with {@link com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor}
       */
      return;
    }

    ZenCodingTemplate zenCodingTemplate = CustomLiveTemplate.EP_NAME.findExtension(ZenCodingTemplate.class);
    if (zenCodingTemplate != null) {
      zenCodingTemplate.addCompletions(parameters, result);
    }
  }
}
