// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.rename;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.sh.highlighting.ShOccurrencesHighlightingSuppressor;
import com.intellij.sh.backend.highlighting.ShTextOccurrencesUtil;
import com.intellij.sh.lexer.ShTokenTypes;
import com.intellij.sh.psi.ShFile;
import com.intellij.sh.psi.ShFunctionDefinition;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

final class ShRenameHandler implements RenameHandler {
  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    ShFile file = ObjectUtils.tryCast(dataContext.getData(CommonDataKeys.PSI_FILE), ShFile.class);
    return editor != null && file != null
           && ShOccurrencesHighlightingSuppressor.isOccurrencesHighlightingEnabled(editor, file)
           && ShRenameAllOccurrencesHandler.INSTANCE.isEnabled(editor, editor.getCaretModel().getPrimaryCaret(), dataContext)
           && isRenameAvailable(editor, file)
      ;
  }

  private static boolean isRenameAvailable(@NotNull Editor editor, @NotNull ShFile file) {
    if (editor.getCaretModel().getPrimaryCaret().hasSelection()) return true;
    TextRange textRange = ShTextOccurrencesUtil.findTextRangeOfIdentifierAtCaret(editor);
    if (textRange != null) {
      PsiElement element = file.findElementAt(textRange.getStartOffset());
      if (element != null && ShTokenTypes.keywords.contains(PsiUtilCore.getElementType(element))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element instanceof ShFunctionDefinition) {
      PsiElementRenameHandler.invoke(element, project, file, editor);
    } else {
      ShRenameAllOccurrencesHandler.INSTANCE.execute(editor, editor.getCaretModel().getPrimaryCaret(), null);
    }
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
  }
}
