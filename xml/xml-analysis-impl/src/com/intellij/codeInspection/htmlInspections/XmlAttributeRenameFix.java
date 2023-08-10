// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class XmlAttributeRenameFix implements LocalQuickFix, HighPriorityAction {
  private final String name;

  public XmlAttributeRenameFix(XmlAttributeDescriptor attr) {
    name = attr.getName();
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return XmlAnalysisBundle.message("html.quickfix.rename.attribute.family");
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return XmlAnalysisBundle.message("html.quickfix.rename.attribute.text", name);
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    XmlAttribute attribute = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), XmlAttribute.class);
    if (attribute == null) return;
    attribute.setName(name);
  }
}
