// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.quickFixes;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;

/**
 * @author Eugene Zhuravlev
 */
public class ChangeFieldTypeFix extends QuickFix {
  private final PsiField myField;
  private final PsiType myNewType;

  public ChangeFieldTypeFix(GuiEditor uiEditor, PsiField field, PsiType uiComponentType) {
    super(uiEditor, gettext(field, uiComponentType), null);
    myField = field;
    myNewType = uiComponentType;
  }

  private static @Nls String gettext(PsiField field, PsiType uiComponentType) {
    return UIDesignerBundle.message("action.change.field.type",
                                         field.getName(), field.getType().getCanonicalText(), uiComponentType.getCanonicalText());
  }

  @Override
  public void run() {
    final PsiFile psiFile = myField.getContainingFile();
    if (psiFile == null) return;
    if (!FileModificationService.getInstance().preparePsiElementForWrite(psiFile)) return;
    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(myField.getProject(), () -> {
      try {
        final PsiManager manager = myField.getManager();
        myField.getTypeElement().replace(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeElement(myNewType));
      }
      catch (final IncorrectOperationException e) {
        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showErrorDialog(myEditor, UIDesignerBundle.message("error.cannot.change.field.type", myField.getName(), e.getMessage()),
                                       CommonBundle.getErrorTitle()));
      }
    }, getName(), null));
  }
}
