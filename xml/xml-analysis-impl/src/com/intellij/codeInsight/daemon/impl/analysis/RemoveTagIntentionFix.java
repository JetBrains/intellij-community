// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoveTagIntentionFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myTagName;

  public RemoveTagIntentionFix(final String name, final @NotNull XmlTag tag) {
    super(tag);
    myTagName = name;
  }

  @Override
  public @NotNull String getText() {
    return XmlAnalysisBundle.message("xml.quickfix.remove.tag.text", myTagName);
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return XmlAnalysisBundle.message("xml.quickfix.remove.tag.family");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final XmlTag next = editor != null ? PsiTreeUtil.getNextSiblingOfType(startElement, XmlTag.class) : null;
    final XmlTag prev = editor != null ? PsiTreeUtil.getPrevSiblingOfType(startElement, XmlTag.class) : null;

    startElement.delete();

    if (editor != null) {
      if (next != null) {
        editor.getCaretModel().moveToOffset(next.getTextRange().getStartOffset());
      }
      else if (prev != null) {
        editor.getCaretModel().moveToOffset(prev.getTextRange().getEndOffset());
      }
    }
  }
}
