/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
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
 *         Date: Jun 15, 2005
 */
public class ChangeBoundFieldTypeFix implements IntentionAction {
  private final PsiField myField;
  private final PsiType myTypeToSet;

  public ChangeBoundFieldTypeFix(PsiField field, PsiType typeToSet) {
    myField = field;
    myTypeToSet = typeToSet;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("uidesigner.change.bound.field.type");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    CommandProcessor.getInstance().executeCommand(myField.getProject(), new Runnable() {
      public void run() {
        try {
          final PsiManager manager = myField.getManager();
          myField.getTypeElement().replace(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeElement(myTypeToSet));
        }
        catch (final IncorrectOperationException e) {
          ApplicationManager.getApplication().invokeLater(new Runnable(){
            public void run() {
              Messages.showErrorDialog(myField.getProject(),
                                       QuickFixBundle.message("cannot.change.field.exception", myField.getName(), e.getLocalizedMessage()),
                                       CommonBundle.getErrorTitle());
            }
          });
        }
      }
    }, getText(), null);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
