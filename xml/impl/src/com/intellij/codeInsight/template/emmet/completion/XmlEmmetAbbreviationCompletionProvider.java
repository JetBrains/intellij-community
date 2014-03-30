/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.template.CustomLiveTemplateBase;
import com.intellij.codeInsight.template.emmet.generators.XmlZenCodingGeneratorImpl;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.impl.CustomLiveTemplateLookupElement;
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElement;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import org.jetbrains.annotations.NotNull;

public class XmlEmmetAbbreviationCompletionProvider extends EmmetAbbreviationCompletionProvider {
  @Override
  protected boolean isAvailable(CompletionParameters parameters) {
    final EmmetOptions emmetOptions = EmmetOptions.getInstance();
    return super.isAvailable(parameters) && emmetOptions.isEmmetEnabled()  && emmetOptions.isPreviewEnabled();
  }

  @Override
  protected LiveTemplateLookupElement createLookupElement(@NotNull CustomLiveTemplateBase customLiveTemplate, @NotNull final TemplateImpl template) {
    return new CustomLiveTemplateLookupElement(customLiveTemplate, template.getKey(), template.getDescription(), "", false, true);
  }

  @Override
  protected ZenCodingGenerator getGenerator() {
    return XmlZenCodingGeneratorImpl.INSTANCE;
  }
}
