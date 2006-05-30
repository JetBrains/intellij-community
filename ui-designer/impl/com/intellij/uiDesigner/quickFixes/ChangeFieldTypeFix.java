/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.uiDesigner.quickFixes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiFile;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.util.IncorrectOperationException;
import com.intellij.CommonBundle;

import java.text.MessageFormat;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 14, 2005
 */
public class ChangeFieldTypeFix extends QuickFix {
  private final PsiField myField;
  private final PsiType myNewType;

  public ChangeFieldTypeFix(GuiEditor uiEditor, PsiField field, PsiType uiComponentType) {
    super(uiEditor, MessageFormat.format(UIDesignerBundle.message("action.change.field.type"),
                                         field.getName(), field.getType().getCanonicalText(), uiComponentType.getCanonicalText()), null);
    myField = field;
    myNewType = uiComponentType;
  }

  public void run() {
    final ReadonlyStatusHandler roHandler = ReadonlyStatusHandler.getInstance(myField.getProject());
    final PsiFile psiFile = myField.getContainingFile();
    if (psiFile == null) return;
    final ReadonlyStatusHandler.OperationStatus status = roHandler.ensureFilesWritable(psiFile.getVirtualFile());
    if (status.hasReadonlyFiles()) return;    
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
                  Messages.showErrorDialog(myEditor, UIDesignerBundle.message("error.cannot.change.field.type", myField.getName(), e.getMessage()),
                                           CommonBundle.getErrorTitle());
                }
              });
            }
          }
        }, getName(), null);
      }
    });
  }
}
