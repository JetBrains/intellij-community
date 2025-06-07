// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.generators.XmlZenCodingGenerator;
import com.intellij.openapi.actionSystem.PopupAction;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

final class EmmetPreviewAction extends BaseCodeInsightAction implements DumbAware, PopupAction {
  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new CodeInsightActionHandler() {
      @Override
      public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
        PsiDocumentManager.getInstance(psiFile.getProject()).commitDocument(editor.getDocument());

        ReadAction.nonBlocking(() -> EmmetPreviewUtil.calculateTemplateText(editor, psiFile, true))
          .finishOnUiThread(ModalityState.any(), templateText -> {
            if (StringUtil.isEmpty(templateText)) {
              CommonRefactoringUtil.showErrorHint(project, editor, XmlBundle.message("cannot.show.preview.for.given.abbreviation"),
                                                  XmlBundle.message("emmet.preview"), null);
              return;
            }

            EmmetPreviewHint.createHint((EditorEx)editor, templateText, psiFile.getFileType()).showHint();
            EmmetPreviewUtil.addEmmetPreviewListeners(editor, psiFile, true);
          })
          .expireWith(() -> editor.isDisposed())
          .submit(AppExecutorUtil.getAppExecutorService());
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }
    };
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return super.isValidForFile(project, editor, psiFile) &&
           ZenCodingTemplate.findApplicableDefaultGenerator(new CustomTemplateCallback(editor, psiFile),
                                                            false) instanceof XmlZenCodingGenerator;
  }
}
