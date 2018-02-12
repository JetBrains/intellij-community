/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull
  @Override
  public Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
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
