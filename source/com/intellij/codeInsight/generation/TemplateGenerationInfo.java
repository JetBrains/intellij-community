package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.template.Template;
import com.intellij.psi.*;

class TemplateGenerationInfo {
  public final Template template;
  public PsiElement element;

  public TemplateGenerationInfo(Template template, PsiElement element) {
    this.template = template;
    this.element = element;
  }
}
