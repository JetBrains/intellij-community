// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.sh.ShBundle;
import com.intellij.sh.statistics.ShFeatureUsagesCollector;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SuppressInspectionIntention implements IntentionAction {
  @NonNls private static final String FEATURE_ACTION_ID = "SuppressInspectionUsed";
  private final String myInspectionCode;
  private final String myMessage;
  private final int myOffset;

  public SuppressInspectionIntention(String message, String inspectionCode, int offset) {
    myInspectionCode = inspectionCode;
    myMessage = message;
    myOffset = offset;
  }

  @NotNull
  @Override
  public String getText() {
    return ShBundle.message("sh.suppress.inspection", myMessage);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return ShBundle.message("sh.shell.script");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (editor == null) return;
    Document document = editor.getDocument();
    int lineStartOffset = DocumentUtil.getLineStartOffset(myOffset, document);
    CharSequence indent = DocumentUtil.getIndent(document, lineStartOffset);
    document.insertString(lineStartOffset, indent + "# shellcheck disable=" + myInspectionCode + "\n");
    ShFeatureUsagesCollector.logFeatureUsage(FEATURE_ACTION_ID);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
