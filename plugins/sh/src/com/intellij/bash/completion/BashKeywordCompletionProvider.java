package com.intellij.bash.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
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
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class BashKeywordCompletionProvider extends CompletionProvider<CompletionParameters> {
  @NotNull
  private final String[] myKeywords;
  private final boolean myWithDescription;

  BashKeywordCompletionProvider(@NotNull String... keywords) {
    this(false, keywords);
  }

  BashKeywordCompletionProvider(boolean withDescription, @NotNull String... keywords) {
    myKeywords = keywords;
    myWithDescription = withDescription;
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
    Project project = parameters.getOriginalFile().getProject();
    for (String keyword : myKeywords) {
      result.addElement(createKeywordLookupElement(project, keyword));
    }
  }

  @NotNull
  private LookupElement createKeywordLookupElement(@NotNull Project project, @NotNull final String keyword) {
    TemplateManagerImpl templateManager = (TemplateManagerImpl) TemplateManager.getInstance(project);
    Template template = TemplateSettings.getInstance().getTemplateById("bash_" + keyword);

    InsertHandler<LookupElement> insertHandler = createTemplateBasedInsertHandler(templateManager, template);
    return LookupElementBuilder
        .create(keyword)
        .withTypeText(template != null && myWithDescription ? template.getDescription() : "")
        .withBoldness(true)
        .withInsertHandler(insertHandler);
  }

  private InsertHandler<LookupElement> createTemplateBasedInsertHandler(@NotNull TemplateManagerImpl templateManager, @Nullable Template template) {
    return (context, item) -> {
      Editor editor = context.getEditor();
      if (template != null) {
        editor.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
        templateManager.startTemplate(editor, template);
      }
      else {
        EditorModificationUtil.insertStringAtCaret(editor, " ");
      }
    };
  }
}
