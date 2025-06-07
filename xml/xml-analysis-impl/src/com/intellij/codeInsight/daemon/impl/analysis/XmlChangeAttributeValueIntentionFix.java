// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class XmlChangeAttributeValueIntentionFix extends PsiElementBaseIntentionAction implements LocalQuickFix, PriorityAction {
  private final String myNewAttributeValue;
  private Priority myPriority;

  public XmlChangeAttributeValueIntentionFix(final String newAttributeValue) {
    myNewAttributeValue = newAttributeValue;
    myPriority = Priority.NORMAL;
  }

  @Override
  public @NotNull String getName() {
    return XmlAnalysisBundle.message("xml.quickfix.change.attribute.value", myNewAttributeValue);
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getText() {
    return myNewAttributeValue != null ? getName() : getFamilyName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return XmlAnalysisBundle.message("xml.quickfix.change.attribute.value.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return getAttribute(element, editor) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    changeAttributeValue(getAttribute(element, editor), editor);
  }

  @Override
  public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
    PsiElement e = descriptor.getPsiElement();
    changeAttributeValue(e, null);
  }

  protected void changeAttributeValue(PsiElement e, Editor editor) {
    final XmlAttribute attr = PsiTreeUtil.getParentOfType(e, XmlAttribute.class, false);
    if (attr == null) return;

    attr.setValue(myNewAttributeValue);

    XmlAttributeValue valueElement = attr.getValueElement();
    if (editor != null && valueElement != null) {
      editor.getCaretModel().moveToOffset(valueElement.getValueTextRange().getStartOffset());
    }
  }

  public void setPriority(Priority value) {
    myPriority = value;
  }

  @Override
  public @NotNull Priority getPriority() {
    return myPriority;
  }

  private static XmlAttribute getAttribute(@NotNull PsiElement element, Editor editor) {
    var result = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
    if (result != null) return result;
    if (element.getTextRange().getStartOffset() == editor.getCaretModel().getOffset()) {
      return PsiTreeUtil.getParentOfType(PsiTreeUtil.prevLeaf(element), XmlAttribute.class);
    }
    return null;
  }
}
