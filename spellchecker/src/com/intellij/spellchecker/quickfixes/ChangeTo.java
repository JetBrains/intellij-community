/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.Anchor;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil.getInjectedEditorForInjectedFile;


public class ChangeTo extends ShowSuggestions implements SpellCheckerQuickFix {

  public static final String FIX_NAME = SpellCheckerBundle.message("change.to");

  public ChangeTo(String wordWithTypo) {
    super(wordWithTypo);
  }

  @NotNull
  public String getFamilyName() {
    return FIX_NAME;
  }

  @NotNull
  public Anchor getPopupActionAnchor() {
    return Anchor.FIRST;
  }


  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (element == null) return;
    final AsyncResult<DataContext> asyncResult = DataManager.getInstance().getDataContextFromFocus();
    asyncResult.doWhenDone((Consumer<DataContext>)context -> {
      Editor editor = CommonDataKeys.EDITOR.getData(context);
      if (editor == null) return;
      
      if (InjectedLanguageManager.getInstance(project).getInjectionHost(element) != null && !(editor instanceof EditorWindow)) {
        editor = getInjectedEditorForInjectedFile(editor, element.getContainingFile());
      }
      
      final TextRange textRange = ((ProblemDescriptorBase)descriptor).getTextRange();
      final int documentLength = editor.getDocument().getTextLength();
      final int endOffset = getDocumentOffset(textRange.getEndOffset(), documentLength);
      final int startOffset = getDocumentOffset(textRange.getStartOffset(), documentLength);
      editor.getSelectionModel().setSelection(startOffset, endOffset);
      final String word = editor.getSelectionModel().getSelectedText();

      if (word == null || StringUtil.isEmpty(word)) {
        return;
      }
      final LookupElement[] items = getSuggestions(project)
        .stream()
        .map(LookupElementBuilder::create)
        .toArray(LookupElement[]::new);
      LookupManager.getInstance(project).showLookup(editor, items);
    });
  }

  private static int getDocumentOffset(int offset, int documentLength) {
    return offset >=0 && offset <= documentLength ? offset : documentLength;
  }
}