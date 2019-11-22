// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.rename;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.sh.ShSupport;
import com.intellij.sh.psi.ShFile;
import org.jetbrains.annotations.NotNull;

public class ShRenameHandler implements RenameHandler {
  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    return editor != null
           && ShSupport.getInstance().isRenameEnabled()
           && ShRenameAllOccurrencesHandler.INSTANCE.isEnabled(editor, editor.getCaretModel().getPrimaryCaret(), dataContext)
           && dataContext.getData(CommonDataKeys.PSI_FILE) instanceof ShFile;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    ShRenameAllOccurrencesHandler.INSTANCE.execute(editor, editor.getCaretModel().getPrimaryCaret(), null);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
  }
}
