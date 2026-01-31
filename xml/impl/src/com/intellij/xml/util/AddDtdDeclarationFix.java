// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttlistDecl;
import com.intellij.psi.xml.XmlConditionalSection;
import com.intellij.psi.xml.XmlDoctype;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlMarkupDecl;
import com.intellij.psi.xml.XmlProlog;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class AddDtdDeclarationFix extends PsiUpdateModCommandQuickFix {
  private final @PropertyKey(resourceBundle = XmlBundle.BUNDLE) String myMessageKey;
  private final String myElementDeclarationName;
  private final String myReference;

  public AddDtdDeclarationFix(
    @PropertyKey(resourceBundle = XmlAnalysisBundle.BUNDLE) String messageKey,
    @NotNull String elementDeclarationName,
    @NotNull PsiReference reference) {
    myMessageKey = messageKey;
    myElementDeclarationName = elementDeclarationName;
    myReference = reference.getCanonicalText();
  }

  @Override
  public @NotNull String getFamilyName() {
    return XmlAnalysisBundle.message(myMessageKey, myReference);
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiFile containingFile = element.getContainingFile();

    @NonNls String prefixToInsert = "";
    @NonNls String suffixToInsert = "";

    final int UNDEFINED_OFFSET = -1;
    int anchorOffset = UNDEFINED_OFFSET;
    PsiElement anchor =
      PsiTreeUtil.getParentOfType(element, XmlElementDecl.class, XmlAttlistDecl.class, XmlEntityDecl.class, XmlConditionalSection.class);
    if (anchor != null) anchorOffset = anchor.getTextRange().getStartOffset();

    if (anchorOffset == UNDEFINED_OFFSET && containingFile.getLanguage() == XMLLanguage.INSTANCE) {
      XmlFile file = (XmlFile)containingFile;
      final XmlProlog prolog = file.getDocument().getProlog();
      assert prolog != null;

      final XmlDoctype doctype = prolog.getDoctype();
      final XmlMarkupDecl markupDecl;

      if (doctype != null) {
        markupDecl = doctype.getMarkupDecl();
      }
      else {
        markupDecl = null;
      }

      if (doctype == null) {
        final XmlTag rootTag = file.getDocument().getRootTag();
        prefixToInsert = "<!DOCTYPE " + ((rootTag != null) ? rootTag.getName() : "null");
        suffixToInsert = ">\n";
      }
      if (markupDecl == null) {
        prefixToInsert += " [\n";
        suffixToInsert = "]" + suffixToInsert;

        if (doctype != null) {
          anchorOffset = doctype.getTextRange().getEndOffset() - 1; // just before last '>'
        }
        else {
          anchorOffset = prolog.getTextRange().getEndOffset();
        }
      }
    }

    if (anchorOffset == UNDEFINED_OFFSET) anchorOffset = element.getTextRange().getStartOffset();

    Document document = containingFile.getFileDocument();
    StringBuilder declaration = new StringBuilder();
    if (!prefixToInsert.isEmpty()) declaration.append(prefixToInsert);
    CharSequence sequence = document.getImmutableCharSequence();
    int pos = anchorOffset - 1;
    while (pos > 0 && (sequence.charAt(pos) == ' ' || sequence.charAt(pos) == '\t')) {
      pos--;
    }
    declaration.append("<!").append(myElementDeclarationName).append(" ").append(myReference).append(" ");
    int finalOffset = declaration.length() + anchorOffset;
    declaration.append(">\n");
    if (pos > 0 && sequence.charAt(pos) == '\n') {
      declaration.append(sequence.subSequence(pos + 1, anchorOffset));
    }
    if (!suffixToInsert.isEmpty()) declaration.append(suffixToInsert);
    document.insertString(anchorOffset, declaration.toString());
    updater.moveCaretTo(finalOffset);
  }
}
