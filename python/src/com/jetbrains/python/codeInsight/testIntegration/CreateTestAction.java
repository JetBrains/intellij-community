// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.testIntegration;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class CreateTestAction extends PsiElementBaseIntentionAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.create.test");
  }


  @Override
  public boolean isAvailable(final @NotNull Project project, final Editor editor, final @NotNull PsiElement element) {
    return PyTestCreationModel.Companion.createByElement(element) != null;
  }

  @Override
  public void invoke(final @NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PyTestCreationModel model =
      PyTestCreationModel.Companion.createByElement(element);
    if (model == null) {
      return;
    }
    if (!CreateTestDialog.userAcceptsTestCreation(project, model)) {
      return;
    }
    CommandProcessor.getInstance().executeCommand(project, () -> {
      PsiFile e = PyTestCreator.generateTestAndNavigate(element, model);
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      documentManager.commitAllDocuments();
    }, CodeInsightBundle.message("intention.create.test"), this);
  }
}
