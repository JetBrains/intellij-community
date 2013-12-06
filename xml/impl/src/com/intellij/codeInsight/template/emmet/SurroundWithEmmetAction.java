/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.emmet;

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class SurroundWithEmmetAction extends BaseCodeInsightAction {
  public SurroundWithEmmetAction() {
    setEnabledInModalContext(true);
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return EmmetOptions.getInstance().isEmmetEnabled() && new ZenCodingTemplate().isApplicable(file, editor.getCaretModel().getOffset(), true);
  }

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new SurroundWithEmmetHandler();
  }

  private static class SurroundWithEmmetHandler implements CodeInsightActionHandler {
    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
      SelectionModel selectionModel = editor.getSelectionModel();
      if (!selectionModel.hasSelection() && !selectionModel.hasBlockSelection()) {
        selectionModel.selectLineAtCaret();
      }

      final Document document = editor.getDocument();
      final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
      if (virtualFile != null) {
        ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(virtualFile);
      }

      String selection = editor.getSelectionModel().getSelectedText();

      final ZenCodingTemplate template = new ZenCodingTemplate();
      if (selection != null && template.isApplicable(file, editor.getCaretModel().getOffset(), true)) {
        selection = selection.trim();
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        template.wrap(selection, new CustomTemplateCallback(editor, file, true));
      }
      else if (!ApplicationManager.getApplication().isUnitTestMode()) {
        HintManager.getInstance().showErrorHint(editor, "Cannot invoke Surround with Emmet in the current context");
      }
    }

    @Override
    public boolean startInWriteAction() {
      return true;
    }
  }
}
