// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.actions.RearrangeCodeProcessor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class RearrangeAttributesIntention implements IntentionAction {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "Rearrange tag attributes";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    XmlTag tag = getTag(editor, file);
    if (tag == null) return false;
    if (tag.getAttributes().length <= 1) return false;
    int offset = editor.getCaretModel().getOffset();
    ASTNode startEnd = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());
    if (startEnd == null || offset <= startEnd.getTextRange().getEndOffset()) return true;
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    XmlTag tag = getTag(editor, file);
    if (tag == null) return;

    TextRange range = tag.getTextRange();
    ASTNode startEnd = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());
    ASTNode name = XmlChildRole.START_TAG_NAME_FINDER.findChild(tag.getNode());

    TextRange reformatRange = new TextRange(name != null ? name.getTextRange().getEndOffset() + 1 : range.getStartOffset() + 1,
                                        startEnd != null ? startEnd.getTextRange().getEndOffset() - 1 : range.getEndOffset());
    RangeMarker marker = editor.getDocument().createRangeMarker(reformatRange);

    new RearrangeCodeProcessor(new ReformatCodeProcessor(project, file, reformatRange, false)) {
      @Override
      public Collection<TextRange> getRangesToFormat(@NotNull PsiFile file, boolean processChangedTextOnly) {
        return Collections.singleton(new TextRange(marker.getStartOffset(), marker.getEndOffset()));
      }
    }.run();
    editor.getCaretModel().moveToOffset(reformatRange.getStartOffset());
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private static XmlTag getTag(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    XmlTag parent = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (parent != null) return parent;

    element = file.findElementAt(offset - 1);
    parent = PsiTreeUtil.getParentOfType(element, XmlTag.class);;
    if (parent != null) return parent;
    return null;
  }
}
