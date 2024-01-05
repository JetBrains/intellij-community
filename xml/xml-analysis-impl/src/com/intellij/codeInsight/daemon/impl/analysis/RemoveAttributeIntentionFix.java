// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoveAttributeIntentionFix extends PsiElementBaseIntentionAction implements LocalQuickFix, PriorityAction {
  private final String myLocalName;

  public RemoveAttributeIntentionFix(final String localName) {
    myLocalName = localName;
  }

  @SuppressWarnings("unused") // to instantiate via extension
  public RemoveAttributeIntentionFix() {
    this(null);
  }

  @Override
  @NotNull
  public String getName() {
    return XmlAnalysisBundle.message("xml.quickfix.remove.attribute.text", myLocalName);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getText() {
    return myLocalName != null ? getName() : getFamilyName();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return XmlAnalysisBundle.message("xml.quickfix.remove.attribute.family");
  }

  @Override
  public @NotNull Priority getPriority() {
    return myLocalName != null ? Priority.LOW : Priority.NORMAL;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return getAttribute(element, editor) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    removeAttribute(getAttribute(element, editor), editor);
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    PsiElement e = descriptor.getPsiElement();
    removeAttribute(e, null);
  }

  protected void removeAttribute(PsiElement e, Editor editor) {
    final XmlAttribute myAttribute = PsiTreeUtil.getParentOfType(e, XmlAttribute.class, false);
    if (myAttribute == null) return;

    PsiElement next = findNextAttribute(myAttribute);
    myAttribute.delete();

    if (next != null && editor != null) {
      editor.getCaretModel().moveToOffset(next.getTextRange().getStartOffset());
    }
  }

  @Nullable
  private static PsiElement findNextAttribute(final XmlAttribute attribute) {
    PsiElement nextSibling = attribute.getNextSibling();
    while (nextSibling != null) {
      if (nextSibling instanceof XmlAttribute) return nextSibling;
      nextSibling = nextSibling.getNextSibling();
    }
    return null;
  }

  private static XmlAttribute getAttribute(PsiElement element, Editor editor) {
    var result = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
    if (result != null) return result;
    if (element.getTextRange().getStartOffset() == editor.getCaretModel().getOffset()) {
      return PsiTreeUtil.getParentOfType(PsiTreeUtil.prevLeaf(element), XmlAttribute.class);
    }
    return null;
  }
}
