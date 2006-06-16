/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.uiDesigner.binding;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 15, 2005
 */
public class ChangeFormComponentTypeFix implements IntentionAction {
  private final PsiPlainTextFile myFormFile;
  private final String myFieldName;
  private final PsiType myComponentTypeToSet;

  public ChangeFormComponentTypeFix(PsiPlainTextFile formFile, String fieldName, PsiType componentTypeToSet) {
    myFormFile = formFile;
    myFieldName = fieldName;
    myComponentTypeToSet = componentTypeToSet;
  }

  public String getText() {
    return QuickFixBundle.message("uidesigner.change.gui.component.type");
  }

  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    CommandProcessor.getInstance().executeCommand(file.getProject(), new Runnable() {
      public void run() {
        final ReadonlyStatusHandler readOnlyHandler = ReadonlyStatusHandler.getInstance(myFormFile.getProject());
        final ReadonlyStatusHandler.OperationStatus status = readOnlyHandler.ensureFilesWritable(myFormFile.getVirtualFile());
        if (!status.hasReadonlyFiles()) {
          FormReferenceProvider.setGUIComponentType(myFormFile, myFieldName, myComponentTypeToSet);
        }
      }
    }, getText(), null);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
