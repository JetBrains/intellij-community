// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.sh.ShBundle;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ShSuppressInspectionIntention implements IntentionAction {
  private final String myInspectionCode;
  private final String myMessage;
  private final int myOffset;

  public ShSuppressInspectionIntention(String message, String inspectionCode, int offset) {
    myInspectionCode = inspectionCode;
    myMessage = message;
    myOffset = offset;
  }

  @Override
  public @NotNull String getText() {
    return ShBundle.message("sh.suppress.inspection", myMessage);
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
    if (editor == null) return;
    Document document = editor.getDocument();
    int lineStartOffset = DocumentUtil.getLineStartOffset(myOffset, document);
    CharSequence indent = DocumentUtil.getIndent(document, lineStartOffset);
    document.insertString(lineStartOffset, indent + "# shellcheck disable=" + myInspectionCode + "\n");
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
