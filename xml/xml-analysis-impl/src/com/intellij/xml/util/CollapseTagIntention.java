// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class CollapseTagIntention implements LocalQuickFix, IntentionAction {

  @Override
  @NotNull
  public String getFamilyName() {
    return XmlAnalysisBundle.message("xml.intention.replace.tag.empty.body.with.empty.end");
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    applyFix(project, descriptor.getPsiElement());
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    XmlTag tag = getTag(editor, file);
    return tag != null && !tag.isEmpty() &&
           tag.getValue().getChildren().length == tag.getValue().getTextElements().length && tag.getValue().getTrimmedText().isEmpty() &&
           CheckTagEmptyBodyInspection.isCollapsibleTag(tag);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    XmlTag tag = getTag(editor, file);
    if (tag != null) {
      applyFix(project, tag);
    }
  }

  private static XmlTag getTag(Editor editor, PsiFile file) {

    int offset = editor.getCaretModel().getOffset();
    FileViewProvider provider = file.getViewProvider();
    for (Language language : provider.getLanguages()) {
      PsiElement element = provider.findElementAt(offset, language);
      XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      if (tag != null && XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode()) != null) {
        return tag;
      }
    }
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  protected static void applyFix(@NotNull final Project project, @NotNull final PsiElement tag) {
    final ASTNode child = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());
    if (child == null) return;
    final int offset = child.getTextRange().getStartOffset();
    final Document document = tag.getContainingFile().getViewProvider().getDocument();
    assert document != null;
    document.replaceString(offset, tag.getTextRange().getEndOffset(), "/>");
    SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(tag);
    PsiDocumentManager.getInstance(project).commitDocument(document);
    PsiElement restored = pointer.getElement();
    if (restored != null) {
      CodeStyleManager.getInstance(project).reformat(restored);
    }
  }
}
