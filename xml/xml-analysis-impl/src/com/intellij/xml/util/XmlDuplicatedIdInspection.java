// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlDuplicatedIdInspection extends XmlSuppressableInspectionTool implements UnfairLocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    // Enable check only for plain XML and HTML files
    if (holder.getFile().getLanguage() != XMLLanguage.INSTANCE
        && holder.getFile().getLanguage() != HTMLLanguage.INSTANCE) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new XmlElementVisitor() {
      @Override
      public void visitXmlAttributeValue(final @NotNull XmlAttributeValue value) {
        if (value.getTextRange().isEmpty()) {
          return;
        }
        final PsiFile file = value.getContainingFile();
        PsiFile baseFile = PsiUtilCore.getTemplateLanguageFile(file);
        if (baseFile != file && !(baseFile instanceof XmlFile)) {
          return;
        }
        final XmlRefCountHolder refHolder = XmlRefCountHolder.getRefCountHolder((XmlFile)file);
        if (refHolder == null) return;

        final PsiElement parent = value.getParent();
        if (!(parent instanceof XmlAttribute)) return;

        final XmlTag tag = (XmlTag)parent.getParent();
        if (tag == null) return;

        checkValue(value, (XmlFile)file, refHolder, tag, holder);
      }
    };
  }

  protected void checkValue(XmlAttributeValue value, XmlFile file, XmlRefCountHolder refHolder, XmlTag tag, ProblemsHolder holder) {
    if (refHolder.isValidatable(tag.getParent()) && refHolder.isDuplicateIdAttributeValue(value)) {
      holder.registerProblem(value, XmlAnalysisBundle.message("xml.inspections.duplicate.id.reference"), ProblemHighlightType.GENERIC_ERROR,
                             ElementManipulators.getValueTextRange(value));
    }
  }
}
