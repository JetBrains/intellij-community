/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class RemoveExtraClosingTagIntentionAction implements LocalQuickFix, IntentionAction {
  @NotNull
  public String getFamilyName() {
    return XmlErrorMessages.message("remove.extra.closing.tag.quickfix");
  }

  @NotNull
  public String getName() {
    return XmlErrorMessages.message("remove.extra.closing.tag.quickfix");
  }


  @NotNull
  public String getText() {
    return getName();
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return true;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement psiElement = file.findElementAt(offset);
    if (psiElement == null || !psiElement.isValid() || !(psiElement instanceof XmlToken)) {
      return;
    }

    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    doFix(project, psiElement);
  }

  public boolean startInWriteAction() {
    return true;
  }

  private void doFix(@NotNull final Project project, @NotNull final PsiElement element) throws IncorrectOperationException {
    final XmlToken endNameToken = (XmlToken) element;
    final PsiElement tagElement = endNameToken.getParent();
    if (!(tagElement instanceof XmlTag)) return;

    final ASTNode astNode = tagElement.getNode();
    final ASTNode endTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(astNode);
    tagElement.deleteChildRange(endTagStart.getPsi(), tagElement.getLastChild());
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!element.isValid() || !(element instanceof XmlToken)) return;
    if (!CodeInsightUtilBase.prepareFileForWrite(element.getContainingFile())) return;

    new WriteCommandAction(project) {
      protected void run(final Result result) throws Throwable {
        doFix(project, element);
      }
    }.execute();
  }
}
