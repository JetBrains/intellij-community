// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.highlighting;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class ShOccurrencesHighlightUsagesHandler extends HighlightUsagesHandlerBase<PsiElement> {
  ShOccurrencesHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file) {
    super(editor, file);
  }

  @Override
  public @NotNull List<PsiElement> getTargets() {
    return Collections.singletonList(myFile);
  }

  @Override
  protected void selectTargets(@NotNull List<? extends PsiElement> targets, @NotNull Consumer<? super List<? extends PsiElement>> selectionConsumer) {
    selectionConsumer.consume(targets);
  }

  @Override
  public void computeUsages(@NotNull List<? extends PsiElement> targets) {
    TextRange textRange = ShTextOccurrencesUtil.findTextRangeOfIdentifierAtCaret(myEditor);
    if (textRange != null) {
      CharSequence documentText = StringUtil.newBombedCharSequence(myEditor.getDocument().getImmutableCharSequence(), 3000);
      boolean hasSelection = myEditor.getCaretModel().getPrimaryCaret().hasSelection();
      ShTextOccurrencesUtil.consumeAllOccurrences(
        documentText,
        textRange.subSequence(documentText),
        !hasSelection, range -> myReadUsages.add(InjectedLanguageManager.getInstance(myFile.getProject()).injectedToHost(myFile, range)));
    }
  }
}
