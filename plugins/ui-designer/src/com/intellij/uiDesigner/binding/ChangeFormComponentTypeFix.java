// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.binding;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 */
public class ChangeFormComponentTypeFix implements IntentionAction {
  private final PsiPlainTextFile myFormFile;
  private final String myFieldName;
  private final String myComponentTypeToSet;

  public ChangeFormComponentTypeFix(PsiPlainTextFile formFile, String fieldName, PsiType componentTypeToSet) {
    myFormFile = formFile;
    myFieldName = fieldName;
    if (componentTypeToSet instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType)componentTypeToSet).resolve();
      if (psiClass != null) {
        myComponentTypeToSet = ClassUtil.getJVMClassName(psiClass);
      }
      else {
        myComponentTypeToSet = ((PsiClassType) componentTypeToSet).rawType().getCanonicalText();
      }
    }
    else {
      myComponentTypeToSet = componentTypeToSet.getCanonicalText();
    }
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("uidesigner.change.gui.component.type");
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
    CommandProcessor.getInstance().executeCommand(file.getProject(), () -> {
      final ReadonlyStatusHandler readOnlyHandler = ReadonlyStatusHandler.getInstance(myFormFile.getProject());
      final ReadonlyStatusHandler.OperationStatus status =
        readOnlyHandler.ensureFilesWritable(Collections.singletonList(myFormFile.getVirtualFile()));
      if (!status.hasReadonlyFiles()) {
        FormReferenceProvider.setGUIComponentType(myFormFile, myFieldName, myComponentTypeToSet);
      }
    }, getText(), null);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
