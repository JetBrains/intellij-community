package com.intellij.bash.completion;

import com.intellij.bash.template.BashContextType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BashKeywordCompletionProvider extends CompletionProvider<CompletionParameters> {

  @NotNull
  private final String[] myKeywords;

  public BashKeywordCompletionProvider(@NotNull String... myKeywords) {
    this.myKeywords = myKeywords;
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
    for (String keyword : myKeywords) {
      result.addElement(createKeywordLookupElement(keyword));
    }
  }

  @NotNull
  private LookupElement createKeywordLookupElement(@NotNull final String keyword) {
    InsertHandler<LookupElement> insertHandler = createTemplateBasedInsertHandler("bash_" + keyword);
    return LookupElementBuilder.create(keyword).withBoldness(true).withInsertHandler(insertHandler);
  }

  @Nullable
  private InsertHandler<LookupElement> createTemplateBasedInsertHandler(@NotNull String templateId) {
    return (context, item) -> {
      TemplateManagerImpl templateManager = (TemplateManagerImpl) TemplateManager.getInstance(context.getProject());
      Template template = TemplateSettings.getInstance().getTemplateById(templateId);
      Editor editor = context.getEditor();
      if (template != null) {
        editor.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
        templateManager.startTemplate(editor, template);
      } else {
        EditorModificationUtil.insertStringAtCaret(editor, " ");
      }
    };
  }
}
