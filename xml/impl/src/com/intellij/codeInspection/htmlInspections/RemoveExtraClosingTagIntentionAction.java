/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class RemoveExtraClosingTagIntentionAction implements LocalQuickFix {
  @NotNull
  public String getFamilyName() {
    return XmlErrorMessages.message("remove.extra.closing.tag.quickfix");
  }

  @NotNull
  public String getName() {
    return XmlErrorMessages.message("remove.extra.closing.tag.quickfix");
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!element.isValid() || !(element instanceof XmlToken)) return;
    if (!CodeInsightUtilBase.prepareFileForWrite(element.getContainingFile())) return;

    new WriteCommandAction(project) {
      protected void run(final Result result) throws Throwable {
        final XmlToken endNameToken = (XmlToken)element;
        final PsiElement tagElement = endNameToken.getParent();
        if (!(tagElement instanceof XmlTag)) return;

        final ASTNode astNode = tagElement.getNode();
        final ASTNode endTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(astNode);
        tagElement.deleteChildRange(endTagStart.getPsi(), tagElement.getLastChild());
      }
    }.execute();
  }
}
