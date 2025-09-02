// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XmlEnclosingTagUnwrapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class RemoveTagAndPromoteChildrenIntentionAction implements IntentionAction {
  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return XmlAnalysisBundle.message("xml.quickfix.remove.tag.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    XmlTag tag = getTag(editor, psiFile);
    if (tag == null) return false;
    int offset = editor.getCaretModel().getOffset();
    ASTNode startEnd = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());
    if (startEnd == null || offset <= startEnd.getStartOffset()) return true;
    ASTNode endStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(tag.getNode());
    if (endStart == null || offset >= startEnd.getStartOffset()) return true;
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    new XmlEnclosingTagUnwrapper().unwrap(editor, getTag(editor, psiFile));
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private static XmlTag getTag(Editor editor, PsiFile psiFile) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = psiFile.findElementAt(offset);
    PsiElement parent = element != null ? element.getParent() : null;
    if (parent instanceof XmlTag) return (XmlTag)parent;
    if (parent instanceof XmlAttribute) return null;

    element = psiFile.findElementAt(offset - 1);
    parent = element != null ? element.getParent() : null;
    if (parent instanceof XmlTag) return (XmlTag)parent;
    return null;
  }

}
