// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EscapeCharacterIntentionFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  @SafeFieldForPreview
  private final TextRange range;
  private final String original;
  private final String replacement;

  public EscapeCharacterIntentionFix(@NotNull PsiElement element,
                                     @NotNull TextRange rangeWithinElement,
                                     @NotNull String original,
                                     @NotNull String replacement) {
    super(element);
    this.range = rangeWithinElement;
    this.original = original;
    this.replacement = replacement;
  }

  @Override
  public @NotNull String getText() {
    return XmlAnalysisBundle.message("xml.quickfix.escape.character", original, replacement);
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiFile topLevelFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(psiFile);
    Document document = topLevelFile.getViewProvider().getDocument();
    assert document != null;
    var startOffset = InjectedLanguageManager.getInstance(project).injectedToHost(startElement, startElement.getTextRange()).getStartOffset();
    document.replaceString(startOffset + range.getStartOffset(), startOffset + range.getEndOffset(), replacement);
  }
}
