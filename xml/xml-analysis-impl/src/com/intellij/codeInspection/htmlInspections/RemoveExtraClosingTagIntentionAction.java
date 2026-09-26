// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;

public class RemoveExtraClosingTagIntentionAction implements LocalQuickFix, IntentionAction {
  @Override
  public @NotNull String getFamilyName() {
    return XmlAnalysisBundle.message("xml.quickfix.remove.extra.closing.tag");
  }


  @Override
  public @NotNull String getText() {
    return getName();
  }

  @Override
  public boolean isAvailable(final @NotNull Project project, final Editor editor, final PsiFile psiFile) {
    PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
    return psiElement instanceof XmlToken && 
           (psiElement.getParent() instanceof XmlTag || psiElement.getParent() instanceof PsiErrorElement);
  }

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile psiFile) throws IncorrectOperationException {
    doFix(Objects.requireNonNull(psiFile.findElementAt(editor.getCaretModel().getOffset())).getParent());
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private static void doFix(@NotNull PsiElement tagElement) throws IncorrectOperationException {
    if (tagElement instanceof PsiErrorElement) {
      Collection<OuterLanguageElement> outers = PsiTreeUtil.findChildrenOfType(tagElement, OuterLanguageElement.class);
      String replacement = StringUtil.join(outers, PsiElement::getText, "");
      Document document = getDocument(tagElement);
      if (document != null && !replacement.isEmpty()) {
        TextRange range = tagElement.getTextRange();
        document.replaceString(range.getStartOffset(), range.getEndOffset(), replacement);
      } else {
        tagElement.delete();
      }
    }
    else {
      final ASTNode astNode = tagElement.getNode();
      if (astNode != null) {
        final ASTNode endTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(astNode);
        if (endTagStart != null) {
          Document document = getDocument(tagElement);
          if (document != null) {
            document.deleteString(endTagStart.getStartOffset(), tagElement.getLastChild().getTextRange().getEndOffset());
          }
        }
      }
    }
  }

  private static Document getDocument(@NotNull PsiElement tagElement) {
    return tagElement.getContainingFile().getViewProvider().getDocument();
  }

  @Override
  public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof XmlToken)) return;

    doFix(element.getParent());
  }
}
