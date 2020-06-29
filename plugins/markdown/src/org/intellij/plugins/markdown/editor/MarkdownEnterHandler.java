// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownBlockQuoteImpl;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFenceImpl;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItemImpl;
import org.jetbrains.annotations.NotNull;

public class MarkdownEnterHandler extends EnterHandlerDelegateAdapter {
  @Override
  public Result preprocessEnter(@NotNull PsiFile file,
                                @NotNull Editor editor,
                                @NotNull Ref<Integer> caretOffset,
                                @NotNull Ref<Integer> caretAdvance,
                                @NotNull DataContext dataContext,
                                EditorActionHandler originalHandler) {
    PsiElement psiElement = file.findElementAt(caretOffset.get() - 1);
    if (psiElement == null) {
      return Result.Continue;
    }

    if (!shouldHandle(editor, dataContext, psiElement)) {
      return Result.Continue;
    }

    if (processCodeFence(editor, psiElement)) return Result.Stop;
    if (processBlockQuote(editor, psiElement)) return Result.Stop;

    return Result.Continue;
  }

  private static boolean processBlockQuote(@NotNull Editor editor, @NotNull PsiElement element) {
    MarkdownBlockQuoteImpl blockQuote = PsiTreeUtil.getParentOfType(element, MarkdownBlockQuoteImpl.class);
    PsiFile file = element.getContainingFile();
    if (blockQuote != null) {
      MarkdownCustomCodeStyleSettings markdown = CodeStyle.getCustomSettings(file, MarkdownCustomCodeStyleSettings.class);

      String toAdd = ">";
      if (markdown.FORCE_ONE_SPACE_AFTER_BLOCKQUOTE_SYMBOL) {
        toAdd += " ";
      }

      MarkdownListItemImpl listItem = PsiTreeUtil.getParentOfType(blockQuote, MarkdownListItemImpl.class);
      if (listItem == null) {
        EditorModificationUtil.insertStringAtCaret(editor, "\n" + toAdd);
      }
      else {
        String indent = StringUtil.repeat(" ", blockQuote.getTextOffset() - listItem.getTextOffset());
        EditorModificationUtil.insertStringAtCaret(editor, "\n" + indent + toAdd);
      }
      return true;
    }

    return false;
  }

  private static boolean processCodeFence(@NotNull Editor editor, @NotNull PsiElement element) {
    PsiLanguageInjectionHost codeFence = InjectedLanguageManager.getInstance(element.getProject()).getInjectionHost(element);
    if (!(codeFence instanceof MarkdownCodeFenceImpl)) {
      codeFence = PsiTreeUtil.getParentOfType(element, MarkdownCodeFenceImpl.class);
    }

    if (codeFence != null) {
      EditorModificationUtil.insertStringAtCaret(editor, "\n" + MarkdownCodeFenceImpl.calculateIndent((MarkdownPsiElement)codeFence));
      return true;
    }

    return false;
  }

  private static boolean shouldHandle(@NotNull Editor editor, @NotNull DataContext dataContext, @NotNull PsiElement element) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return false;
    }

    Document editorDocument = editor.getDocument();
    if (!editorDocument.isWritable()) {
      return false;
    }

    PsiFile topLevelFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(element);
    if (!(topLevelFile instanceof MarkdownFile)) {
      return false;
    }

    if (editor.isViewer()) {
      return false;
    }

    return true;
  }
}