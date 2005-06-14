/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.uiDesigner.quickFixes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.uiDesigner.GuiEditor;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 14, 2005
 */
public class ChangeFieldTypeFix extends QuickFix {
  private final PsiField myField;
  private final PsiType myNewType;

  public ChangeFieldTypeFix(GuiEditor uiEditor, PsiField field, PsiType uiComponentType) {
    super(uiEditor, "Change field '" + field.getName() + "' type from '" + field.getType().getCanonicalText() + "' to '" + uiComponentType.getCanonicalText() + "'");
    myField = field;
    myNewType = uiComponentType;
  }

  public void run() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(myField.getProject(), new Runnable() {
          public void run() {
            try {
              final PsiManager manager = myField.getManager();
              myField.getTypeElement().replace(manager.getElementFactory().createTypeElement(myNewType));
            }
            catch (final IncorrectOperationException e) {
              ApplicationManager.getApplication().invokeLater(new Runnable(){
                public void run() {
                  Messages.showErrorDialog(myEditor, "Cannot change field '"+ myField.getName() +"' type.\nReason: " + e.getMessage(), "Error");
                }
              });
            }
          }
        }, getName(), null);
      }
    });
  }
}
