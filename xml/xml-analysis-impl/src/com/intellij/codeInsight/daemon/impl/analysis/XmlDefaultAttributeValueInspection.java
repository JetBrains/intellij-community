// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlDefaultAttributeValueInspection extends XmlSuppressableInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
        PsiElement parent = value.getParent();
        if (!(parent instanceof XmlAttribute)) {
          return;
        }
        if (parent.getParent() instanceof HtmlTag || HtmlUtil.isHtmlFile(parent.getParent())) return;
        XmlAttributeDescriptor descriptor = ((XmlAttribute)parent).getDescriptor();
        if (descriptor == null || descriptor.isRequired()) {
          return;
        }
        String defaultValue = descriptor.getDefaultValue();
        if (defaultValue != null && defaultValue.equals(value.getValue()) && !value.getTextRange().isEmpty()) {
          holder.registerProblem(value, XmlAnalysisBundle.message("xml.inspections.redundant.default.attribute.value.assignment"), ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                 new RemoveAttributeIntentionFix(((XmlAttribute)parent).getLocalName()));
        }
      }
    };
  }
}
