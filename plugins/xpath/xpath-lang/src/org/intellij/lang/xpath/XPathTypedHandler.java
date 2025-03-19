// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class XPathTypedHandler extends TypedHandlerDelegate {
  @Override
  public @NotNull Result checkAutoPopup(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (charTyped == '$') {
      if (!(file instanceof XPathFile)) return Result.CONTINUE;

      AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null);
      return Result.CONTINUE;
    } else if (charTyped == '.') {
      if (!(file instanceof XPathFile)) return Result.CONTINUE;
      return Result.STOP;
    } else {
      return super.checkAutoPopup(charTyped, project, editor, file);
    }
  }
}