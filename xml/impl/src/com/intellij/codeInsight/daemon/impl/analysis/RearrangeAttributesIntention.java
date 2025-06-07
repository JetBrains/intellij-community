// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

final class RearrangeAttributesIntention implements IntentionAction {
  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return XmlBundle.message("rearrange.tag.attributes");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    XmlTag tag = getTag(editor, psiFile);
    if (tag == null) return false;
    if (tag.getAttributes().length <= 1) return false;
    int offset = editor.getCaretModel().getOffset();
    ASTNode startEnd = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());
    if (startEnd == null || offset <= startEnd.getTextRange().getEndOffset()) return true;
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    XmlTag tag = getTag(editor, psiFile);
    if (tag == null) return;

    TextRange range = tag.getTextRange();
    ASTNode startEnd = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());
    ASTNode name = XmlChildRole.START_TAG_NAME_FINDER.findChild(tag.getNode());

    TextRange reformatRange = new TextRange(name != null ? name.getTextRange().getEndOffset() + 1 : range.getStartOffset() + 1,
                                        startEnd != null ? startEnd.getTextRange().getEndOffset() - 1 : range.getEndOffset());
    RangeMarker marker = editor.getDocument().createRangeMarker(reformatRange);

    new RearrangeCodeProcessor(new ReformatCodeProcessor(project, psiFile, reformatRange, false)) {
      @Override
      public Collection<TextRange> getRangesToFormat(@NotNull PsiFile psiFile, boolean processChangedTextOnly) {
        return Collections.singleton(marker.getTextRange());
      }
    }.run();
    editor.getCaretModel().moveToOffset(reformatRange.getStartOffset());
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private static XmlTag getTag(@Nullable Editor editor, PsiFile psiFile) {
    if (!(psiFile instanceof XmlFile)) return null;
    
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = psiFile.findElementAt(offset);
    XmlTag parent = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (parent != null) return parent;

    element = psiFile.findElementAt(offset - 1);
    parent = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (parent != null) return parent;
    return null;
  }
}
