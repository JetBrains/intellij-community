package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

public class MethodNameMacro implements Macro {

  public String getName() {
    return "methodName";
  }

  public String getDescription() {
    return "methodName()";
  }

  public String getDefaultValue() {
    return "a";
  }

  public Result calculateResult(Expression[] params, final ExpressionContext context) {
    Project project = context.getProject();
    int templateStartOffset = context.getTemplateStartOffset();
    final int offset = templateStartOffset > 0 ? context.getTemplateStartOffset() - 1 : context.getTemplateStartOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);
    while(place != null){
      if (place instanceof PsiMethod){
        return new TextResult(((PsiMethod)place).getName());
      } else if (place instanceof PsiClassInitializer) {
        return ((PsiClassInitializer) place).hasModifierProperty(PsiModifier.STATIC) ?
               new TextResult("'static initializer'") :
               new TextResult("'instance initializer'");
      }
      place = place.getParent();
    }
    return null;
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    return null;
  }

  public LookupItem[] calculateLookupItems(Expression[] params, final ExpressionContext context) {
    return null;
  }
}
