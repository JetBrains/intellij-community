package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class PostfixLiveTemplate implements CustomLiveTemplate {
  public static final Logger LOG = Logger.getInstance(PostfixLiveTemplate.class);
  private final HashMap<String, PostfixTemplate> myTemplates = ContainerUtil.newHashMap();

  public PostfixLiveTemplate() {
    for (PostfixTemplate template : PostfixTemplate.EP_NAME.getExtensions()) {
      PostfixTemplate previousValue = myTemplates.put(template.getKey(), template);
      if (previousValue != null) {
        LOG.error("Can't register postfix template. Duplicated key: " + template.getKey());
      }
    }
  }

  @Nullable
  @Override
  public String computeTemplateKey(@NotNull CustomTemplateCallback callback) {
    Editor editor = callback.getEditor();
    CharSequence documentContent = editor.getDocument().getCharsSequence();
    int currentOffset = editor.getCaretModel().getOffset();
    int startOffset = currentOffset;
    while (startOffset > 0) {
      char currentChar = documentContent.charAt(startOffset - 1);
      if (!Character.isJavaIdentifierPart(currentChar)) {
        if (currentChar != '.') {
          return null;
        }
        startOffset--;
        break;
      }
      startOffset--;
    }
    String key = String.valueOf(documentContent.subSequence(startOffset, currentOffset));

    PostfixTemplate template = myTemplates.get(key);
    return isApplicableTemplate(template, callback.getContext(), editor) ? key : null;
  }

  @Override
  public void expand(@NotNull final String key, @NotNull final CustomTemplateCallback callback) {
    final PostfixTemplate template = myTemplates.get(key);
    final Editor editor = callback.getEditor();
    if (isApplicableTemplate(template, callback.getContext(), editor)) {
      final PsiFile file = callback.getContext().getContainingFile();
      int currentOffset = editor.getCaretModel().getOffset();
      PsiElement newContext = deleteTemplateKey(file, editor.getDocument(), currentOffset, key);
      newContext = addSemicolonIfNeeded(editor, editor.getDocument(), newContext, currentOffset - key.length());
      expandTemplate(template, editor, newContext);
    }
    else {
      LOG.error("Template not found by key: " + key);
    }
  }

  @Override
  public boolean isApplicable(PsiFile file, int offset, boolean wrapping) {
    return !wrapping && file != null && PsiUtilCore.getLanguageAtOffset(file, offset) == JavaLanguage.INSTANCE;
  }

  @Override
  public boolean supportsWrapping() {
    return false;
  }

  @Override
  public void wrap(@NotNull String selection, @NotNull CustomTemplateCallback callback) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getTitle() {
    return "Postfix";
  }

  @Override
  public char getShortcut() {
    return TemplateSettings.TAB_CHAR; //todo: make it configurable
  }

  public boolean hasCompletionItem(@NotNull PsiFile file, int offset) {
    //todo: extend CustomLiveTemplateBase (IDEA 13) and mark this method as @Override
    return true;
  }

  private static void expandTemplate(@NotNull final PostfixTemplate template,
                                     @NotNull final Editor editor,
                                     @NotNull final PsiElement context) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CommandProcessor.getInstance().executeCommand(context.getProject(), new Runnable() {
          public void run() {
            template.expand(context, editor);
          }
        }, "Expand postfix template", null);
      }
    });
  }

  private static boolean isApplicableTemplate(@Nullable PostfixTemplate template, @NotNull PsiElement context, @NotNull Editor editor) {
    if (template == null || !template.isEnabled()) {
      return false;
    }
    PsiFile file = context.getContainingFile();
    PsiFile copy = (PsiFile)file.copy(); // todo: implement caching

    Document copyDocument = copy.getViewProvider().getDocument();
    assert copyDocument != null;
    int currentOffset = editor.getCaretModel().getOffset();
    PsiElement newContext = deleteTemplateKey(copy, copyDocument, currentOffset, template.getKey());
    newContext = addSemicolonIfNeeded(editor, copyDocument, newContext, currentOffset - template.getKey().length());
    return template.isApplicable(newContext);
  }

  @NotNull
  private static PsiElement deleteTemplateKey(@NotNull final PsiFile file,
                                              @NotNull final Document document,
                                              final int currentOffset,
                                              @NotNull final String key) {
    final int startOffset = currentOffset - key.length();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
          public void run() {
            document.deleteString(startOffset, currentOffset);
            PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
          }
        });
      }
    });
    return CustomTemplateCallback.getContext(file, startOffset > 0 ? startOffset - 1 : startOffset);
  }

  @NotNull
  private static PsiElement addSemicolonIfNeeded(@NotNull final Editor editor,
                                                 @NotNull final Document document,
                                                 @NotNull final PsiElement context,
                                                 final int offset) {
    final Ref<PsiElement> newContext = Ref.create(context);
    final PsiFile file = context.getContainingFile();
    CompletionInitializationContext initializationContext = new CompletionInitializationContext(editor, file, CompletionType.BASIC);
    new JavaCompletionContributor().beforeCompletion(initializationContext);
    if (StringUtil.endsWithChar(initializationContext.getDummyIdentifier(), ';')) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
            public void run() {
              document.insertString(offset, ";");
              PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
              newContext.set(CustomTemplateCallback.getContext(file, offset - 1));
            }
          });
        }
      });
    }
    return newContext.get();
  }
}
