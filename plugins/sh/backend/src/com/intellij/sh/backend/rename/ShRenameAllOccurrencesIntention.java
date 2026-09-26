// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.rename;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.ShortcutProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.sh.ShBundle;
import com.intellij.sh.highlighting.ShOccurrencesHighlightingSuppressor;
import com.intellij.sh.psi.ShFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShRenameAllOccurrencesIntention extends BaseIntentionAction implements ShortcutProvider, HighPriorityAction {
  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public @NotNull String getText() {
    return ShBundle.message("sh.rename.all.occurrences");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return ShOccurrencesHighlightingSuppressor.isOccurrencesHighlightingEnabled(editor, psiFile)
           && ShRenameAllOccurrencesHandler.INSTANCE.isEnabled(editor, editor.getCaretModel().getPrimaryCaret(), null);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    if (psiFile instanceof ShFile && editor != null) {
      ShRenameAllOccurrencesHandler.INSTANCE.execute(editor, editor.getCaretModel().getPrimaryCaret(), null);
    }
  }

  @Override
  public @Nullable ShortcutSet getShortcut() {
    return CommonShortcuts.getRename();
  }
}
