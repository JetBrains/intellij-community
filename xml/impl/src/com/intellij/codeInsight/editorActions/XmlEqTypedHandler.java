/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;

public class XmlEqTypedHandler extends TypedHandlerDelegate {
  private boolean needToInsertQuotes = false;

  @Override
  public Result beforeCharTyped(char c, Project project, Editor editor, PsiFile file, FileType fileType) {
    if (c == '=' && WebEditorOptions.getInstance().isInsertQuotesForAttributeValue()) {
      if (XmlGtTypedHandler.fileContainsXmlLanguage(file)) {
        TypedHandler.commitDocumentIfCurrentCaretIsNotTheFirstOne(editor, project);
        
        PsiElement at = file.findElementAt(editor.getCaretModel().getOffset() - 1);
        PsiElement atParent = at != null ? at.getParent() : null;
        if(atParent instanceof XmlAttribute && ((XmlAttribute)atParent).getValueElement() == null) {
          needToInsertQuotes = ((XmlAttribute)atParent).getValueElement() == null;
        }
      }
    }

    return super.beforeCharTyped(c, project, editor, file, fileType);
  }

  @Override
  public Result charTyped(char c, Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (needToInsertQuotes) {
      int offset = editor.getCaretModel().getOffset();
      PsiElement fileContext = file.getContext();
      String toInsert= "\"\"";

      if(fileContext != null) {
        if (fileContext.getText().startsWith("\"")) toInsert = "''";
      }
      editor.getDocument().insertString(offset, toInsert);
      editor.getCaretModel().moveToOffset(offset + 1);
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
    }
    needToInsertQuotes = false;
    return super.charTyped(c, project, editor, file);
  }
}
