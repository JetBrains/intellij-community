// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck.intention;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.sh.ShBundle;
import com.intellij.sh.shellcheck.ShShellcheckInspection;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ShDisableInspectionIntention implements IntentionAction, LowPriorityAction, Iconable {
  private final String myInspectionCode;
  private final String myMessage;

  public ShDisableInspectionIntention(String message, String inspectionCode) {
    myInspectionCode = inspectionCode;
    myMessage = message;
  }

  @Override
  public @NotNull String getText() {
    return ShBundle.message("sh.disable.inspection.text", myMessage);
  }

  @Override
  public @NotNull String getFamilyName() {
    return ShBundle.message("sh.shell.script");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    if (psiFile == null) return;

    InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project, it -> {
      ShShellcheckInspection tool = (ShShellcheckInspection)it.getUnwrappedTool(ShShellcheckInspection.SHORT_NAME, psiFile);
      if (tool != null) {
        tool.disableInspection(myInspectionCode);
      }
    });
    DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Actions.Cancel;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return null;
  }
}
