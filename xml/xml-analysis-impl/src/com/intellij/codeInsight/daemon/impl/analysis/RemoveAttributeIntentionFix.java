// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class RemoveAttributeIntentionFix implements LocalQuickFix, IntentionAction {
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
    return myLocalName != null? getName() : getFamilyName();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return XmlAnalysisBundle.message("xml.quickfix.remove.attribute.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return getAttribute(editor, file) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    removeAttribute(getAttribute(editor, file), editor);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
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
      nextSibling =  nextSibling.getNextSibling();
    }
    return null;
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
