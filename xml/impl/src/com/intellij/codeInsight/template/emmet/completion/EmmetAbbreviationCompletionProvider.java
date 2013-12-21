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

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingListener;
import com.intellij.codeInsight.template.emmet.ZenCodingTemplate;
import com.intellij.codeInsight.template.emmet.filters.SingleLineEmmetFilter;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElement;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Ref;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * User: zolotov
 * Date: 8/9/13
 */
abstract public class EmmetAbbreviationCompletionProvider extends CompletionProvider<CompletionParameters> {
  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    if (!isAvailable(parameters)) {
      return;
    }

    final ZenCodingGenerator generator = getGenerator();
    if (!generator.isMyContext(parameters.getPosition(), false) || !generator.isAppliedByDefault(parameters.getPosition())) {
      return;
    }

    final PsiFile file = parameters.getPosition().getContainingFile();
    final Editor editor = parameters.getEditor();

    final String completionPrefix = result.getPrefixMatcher().getPrefix();
    final String templatePrefix = LiveTemplateCompletionContributor.findLiveTemplatePrefix(file, editor, completionPrefix);

    if (LiveTemplateCompletionContributor.findApplicableTemplate(file, parameters.getOffset(), templatePrefix) != null) {
      // exclude perfect matches with existing templates because LiveTemplateCompletionContributor handles it
      result.restartCompletionOnPrefixChange(StandardPatterns.string().startsWith(templatePrefix));
      return;
    }

    final Ref<TemplateImpl> generatedTemplate = new Ref<TemplateImpl>();
    final CustomTemplateCallback callback = new CustomTemplateCallback(editor, file, false) {
      @Override
      public void deleteTemplateKey(String key) {
      }

      @Override
      public void startTemplate(Template template, Map<String, String> predefinedValues, TemplateEditingListener listener) {
        if (template instanceof TemplateImpl && !((TemplateImpl)template).isDeactivated()) {
          generatedTemplate.set((TemplateImpl)template);
        }
      }
    };

    final Collection<SingleLineEmmetFilter> extraFilters = ContainerUtil.newLinkedList(new SingleLineEmmetFilter());
    ZenCodingTemplate.expand(templatePrefix, callback, null, generator, extraFilters, false);
    if (!generatedTemplate.isNull()) {
      result = result.withPrefixMatcher(templatePrefix);
      final TemplateImpl template = generatedTemplate.get();
      template.setKey(templatePrefix);
      template.setDescription(template.getTemplateText());
      result.addElement(createLookupElement(template));
      result.restartCompletionOnPrefixChange(StandardPatterns.string().startsWith(templatePrefix));
    }
  }

  protected LiveTemplateLookupElement createLookupElement(TemplateImpl template) {
    return new EmmetAbbreviationLookupElement(template, null, true);
  }

  protected abstract ZenCodingGenerator getGenerator();

  protected boolean isAvailable(CompletionParameters parameters) {
    return parameters.getInvocationCount() == 0;
  }
}
