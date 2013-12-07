package org.jetbrains.postfixCompletion.completion;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElement;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.templates.PostfixLiveTemplate;
import org.jetbrains.postfixCompletion.templates.PostfixTemplate;

import static org.jetbrains.postfixCompletion.completion.PostfixTemplateCompletionContributor.getPostfixLiveTemplate;

class PostfixTemplateLookupElement extends LiveTemplateLookupElement {
  @NotNull 
  private final PostfixTemplate myTemplate;

  public PostfixTemplateLookupElement(@NotNull PostfixTemplate template, char shortcut) {
    super(createStubTemplate(template, shortcut), template.getKey(), true, true);
    myTemplate = template;
  }

  @Override
  public void handleInsert(InsertionContext context) {
    context.setAddCompletionChar(false);
    int lengthOfTypedKey = context.getTailOffset() - context.getStartOffset();
    String templateKey = myTemplate.getKey();
    Editor editor = context.getEditor();
    if (lengthOfTypedKey < templateKey.length()) {
      context.getDocument().insertString(context.getTailOffset(), templateKey.substring(lengthOfTypedKey));
      editor.getCaretModel().moveToOffset(context.getTailOffset() + templateKey.length() - lengthOfTypedKey);
    }

    PsiFile file = context.getFile();

    PostfixLiveTemplate postfixLiveTemplate = getPostfixLiveTemplate(file, editor.getCaretModel().getOffset());
    if (postfixLiveTemplate != null) {
      postfixLiveTemplate.expand(templateKey, new CustomTemplateCallback(editor, file, false));
    }
  }

  private static TemplateImpl createStubTemplate(@NotNull PostfixTemplate postfixTemplate, char shortcut) {
    TemplateImpl template = new TemplateImpl(postfixTemplate.getKey(), "postfixTemplate");
    template.setDescription(postfixTemplate.getExample());
    template.setShortcutChar(shortcut);
    return template;
  }
}
