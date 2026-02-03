// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

/**
 * @author  Dmitry Avdeev
 */
public class XmlPiTypedHandler extends TypedHandlerDelegate {

  @Override
  public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (c == '?' && file.getLanguage() == XMLLanguage.INSTANCE) {
      int offset = editor.getCaretModel().getOffset();
      if (offset >= 2 && editor.getDocument().getCharsSequence().charAt(offset - 2) == '<') {
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        PsiElement at = file.findElementAt(offset - 2);
        if (at != null && at.getNode().getElementType() == XmlTokenType.XML_PI_START &&
            editor.getDocument().getText().indexOf("?>", offset) == -1) {

          editor.getDocument().insertString(offset, " ?>");
          AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
        }
      }
    }
    return super.charTyped(c, project, editor, file);
  }
}
