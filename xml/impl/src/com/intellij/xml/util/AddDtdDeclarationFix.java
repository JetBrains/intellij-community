// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class AddDtdDeclarationFix implements LocalQuickFix {
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
  public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
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

    OpenFileDescriptor openDescriptor = new OpenFileDescriptor(project, containingFile.getVirtualFile(), anchorOffset);
    final Editor editor = FileEditorManager.getInstance(project).openTextEditor(openDescriptor, true);
    final TemplateManager templateManager = TemplateManager.getInstance(project);
    final Template t = templateManager.createTemplate("", "");

    if (!prefixToInsert.isEmpty()) t.addTextSegment(prefixToInsert);
    t.addTextSegment("<!" + myElementDeclarationName + " " + myReference + " ");
    t.addEndVariable();
    t.addTextSegment(">\n");
    if (!suffixToInsert.isEmpty()) t.addTextSegment(suffixToInsert);
    templateManager.startTemplate(editor, t);
  }
}
