// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.sh.backend.completion.ShCompletionUtil.endsWithDot;

class ShKeywordCompletionProvider extends CompletionProvider<CompletionParameters> {
  private static final int PRIORITY = 10;

  private final String @NotNull [] myKeywords;
  private final @NotNull EventId myEventId;
  private final boolean myWithDescription;

  ShKeywordCompletionProvider(@NotNull EventId eventId, @NonNls String @NotNull ... keywords) {
    this(eventId, false, keywords);
  }

  ShKeywordCompletionProvider(@NotNull EventId eventId, boolean withDescription, @NonNls String @NotNull ... keywords) {
    myKeywords = keywords;
    myEventId = eventId;
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

  private @NotNull LookupElement createKeywordLookupElement(@NotNull Project project, final @NotNull String keyword) {
    TemplateManagerImpl templateManager = (TemplateManagerImpl) TemplateManager.getInstance(project);
    Template template = TemplateSettings.getInstance().getTemplateById("shell_" + keyword);

    InsertHandler<LookupElement> insertHandler = createTemplateBasedInsertHandler(templateManager, template, myEventId);
    return PrioritizedLookupElement.withPriority(LookupElementBuilder
        .create(keyword)
        .withTypeText(template != null && myWithDescription ? template.getDescription() : "")
        .withBoldness(true)
        .withInsertHandler(insertHandler), PRIORITY);
  }

  private static InsertHandler<LookupElement> createTemplateBasedInsertHandler(@NotNull TemplateManagerImpl templateManager,
                                                                               @Nullable Template template, @NotNull EventId eventId) {
    return (context, item) -> {
      Editor editor = context.getEditor();
      if (template != null) {
        editor.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
        templateManager.startTemplate(editor, template);
      }
      else {
        EditorModificationUtil.insertStringAtCaret(editor, " ");
      }
      eventId.log();
    };
  }
}
