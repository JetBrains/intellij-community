// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xml.util;

import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim Mossienko
 */
public class CheckValidXmlInScriptBodyInspection extends CheckValidXmlInScriptBodyInspectionBase {
  @Override
  protected InsertQuotedCharacterQuickFix createFix(PsiElement psiElement, int offsetInElement) {
    return new InsertQuotedCharacterQuickFix(psiElement, offsetInElement);
  }

  private static class InsertQuotedCharacterQuickFix extends LocalQuickFixOnPsiElement {
    private final int startInElement;

    InsertQuotedCharacterQuickFix(PsiElement psiElement, int startInElement) {
      super(psiElement);
      this.startInElement = startInElement;
    }

    @Override
    @NotNull
    public String getText() {
      return XmlBundle.message("xml.quickfix.unescaped.xml.character.text", getXmlCharacter());
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return XmlBundle.message("xml.quickfix.unescaped.xml.character.family");
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      final PsiFile psiFile = startElement.getContainingFile();
      final TextRange range = startElement.getTextRange();

      final String xmlCharacter = getXmlCharacter();
      String replacement = xmlCharacter.equals("&") ? AMP_ENTITY_REFERENCE : LT_ENTITY_REFERENCE;
      replacement = startElement.getText().replace(xmlCharacter, replacement);

      psiFile.getViewProvider().getDocument().replaceString(
        range.getStartOffset(),
        range.getEndOffset(),
        replacement
      );
    }

    private String getXmlCharacter() {
      return getStartElement().getText().substring(startInElement, startInElement + 1);
    }
  }
}
