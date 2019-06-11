// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.sh.psi.ShFile;
import org.jetbrains.annotations.NotNull;

public class ShTypedHandler extends TypedHandlerDelegate {
  @NotNull
  @Override
  public Result checkAutoPopup(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!(file instanceof ShFile)) return Result.CONTINUE;
    int currentLine = editor.getCaretModel().getPrimaryCaret().getLogicalPosition().line;
    if (currentLine == 0 && (charTyped == '!' || charTyped == '/')) {
      AutoPopupController.getInstance(editor.getProject()).autoPopupMemberLookup(editor, null);
      return Result.STOP;
    }
    return Result.CONTINUE;
  }
}
