// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class XmlChangeAttributeValueIntentionFix implements LocalQuickFix, IntentionAction, PriorityAction {
  private final String myNewAttributeValue;
  private Priority myPriority;

  public XmlChangeAttributeValueIntentionFix(final String newAttributeValue) {
    myNewAttributeValue = newAttributeValue;
    myPriority = Priority.NORMAL;
  }

  @SuppressWarnings("unused") // to instantiate via extension
  public XmlChangeAttributeValueIntentionFix() {
    this(null);
  }

  @Override
  @NotNull
  public String getName() {
    return XmlAnalysisBundle.message("xml.quickfix.change.attribute.value", myNewAttributeValue);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getText() {
    return myNewAttributeValue != null ? getName() : getFamilyName();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return XmlAnalysisBundle.message("xml.quickfix.change.attribute.value.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return getAttribute(editor, file) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    changeAttributeValue(getAttribute(editor, file), editor);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
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

  private static XmlAttribute getAttribute(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    XmlAttribute attribute = PsiTreeUtil.getParentOfType(file.findElementAt(offset), XmlAttribute.class);
    if (attribute == null) {
      attribute = PsiTreeUtil.getParentOfType(file.findElementAt(offset - 1), XmlAttribute.class);
    }
    return attribute;
  }
}
