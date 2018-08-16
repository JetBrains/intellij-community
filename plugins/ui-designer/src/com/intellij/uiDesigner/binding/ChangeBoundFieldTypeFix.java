// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.binding;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class ChangeBoundFieldTypeFix implements IntentionAction {
  private final PsiField myField;
  private final PsiType myTypeToSet;

  public ChangeBoundFieldTypeFix(PsiField field, PsiType typeToSet) {
    myField = field;
    myTypeToSet = typeToSet;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("uidesigner.change.bound.field.type");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    CommandProcessor.getInstance().executeCommand(myField.getProject(), () -> {
      try {
        final PsiManager manager = myField.getManager();
        myField.getTypeElement().replace(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeElement(myTypeToSet));
      }
      catch (final IncorrectOperationException e) {
        ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(myField.getProject(),
                                                                                   QuickFixBundle.message("cannot.change.field.exception", myField.getName(), e.getLocalizedMessage()),
                                                                                   CommonBundle.getErrorTitle()));
      }
    }, getText(), null);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
