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
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.generators.XmlZenCodingGenerator;
import com.intellij.openapi.actionSystem.PopupAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

public class EmmetPreviewAction extends BaseCodeInsightAction implements DumbAware, PopupAction {
  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new CodeInsightActionHandler() {
      @Override
      public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        String templateText = EmmetPreviewUtil.calculateTemplateText(editor, file, true);
        if (StringUtil.isEmpty(templateText)) {
          CommonRefactoringUtil.showErrorHint(project, editor, "Cannot show preview for given abbreviation", "Emmet Preview", null);
          return;
        }

        EmmetPreviewHint.createHint((EditorEx)editor, templateText, file.getFileType()).showHint();
        EmmetPreviewUtil.addEmmetPreviewListeners(editor, file, true);
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }
    };
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return super.isValidForFile(project, editor, file) &&
           ZenCodingTemplate.findApplicableDefaultGenerator(CustomTemplateCallback.getContext(file, CustomTemplateCallback.getOffset(editor)),
                                                            false) instanceof XmlZenCodingGenerator;
  }
}
