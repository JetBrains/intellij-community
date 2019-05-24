// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.sh.statistics.ShFeatureUsagesCollector;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.sh.completion.ShCompletionUtil.endsWithDot;

class ShKeywordCompletionProvider extends CompletionProvider<CompletionParameters> {
  private static final int PRIORITY = 10;

  @NotNull
  private final String[] myKeywords;
  @NotNull
  private final String myFeatureActionId;
  private final boolean myWithDescription;

  ShKeywordCompletionProvider(@NotNull String featureActionId, @NotNull String... keywords) {
    this(featureActionId, false, keywords);
  }

  ShKeywordCompletionProvider(@NotNull String featureActionId, boolean withDescription, @NotNull String... keywords) {
    myKeywords = keywords;
    myFeatureActionId = featureActionId;
    myWithDescription = withDescription;
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
    if (endsWithDot(parameters)) return;

    Project project = parameters.getOriginalFile().getProject();
    for (String keyword : myKeywords) {
      result.addElement(createKeywordLookupElement(project, keyword));
    }
  }

  @NotNull
  private LookupElement createKeywordLookupElement(@NotNull Project project, @NotNull final String keyword) {
    TemplateManagerImpl templateManager = (TemplateManagerImpl) TemplateManager.getInstance(project);
    Template template = TemplateSettings.getInstance().getTemplateById("shell_" + keyword);

    InsertHandler<LookupElement> insertHandler = createTemplateBasedInsertHandler(templateManager, template, myFeatureActionId);
    return PrioritizedLookupElement.withPriority(LookupElementBuilder
        .create(keyword)
        .withTypeText(template != null && myWithDescription ? template.getDescription() : "")
        .withBoldness(true)
        .withInsertHandler(insertHandler), PRIORITY);
  }

  private static InsertHandler<LookupElement> createTemplateBasedInsertHandler(@NotNull TemplateManagerImpl templateManager,
                                                                               @Nullable Template template, @NotNull String featureActionId) {
    return (context, item) -> {
      Editor editor = context.getEditor();
      if (template != null) {
        editor.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
        templateManager.startTemplate(editor, template);
      }
      else {
        EditorModificationUtil.insertStringAtCaret(editor, " ");
      }
      ShFeatureUsagesCollector.logFeatureUsage(featureActionId);
    };
  }
}
