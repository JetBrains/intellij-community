package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

import java.util.ArrayList;

public class ArrayVariableMacro extends VariableTypeMacroBase {
  public String getName() {
    return "arrayVariable";
  }

  public String getDescription() {
    return "arrayVariable()";
  }

  protected PsiVariable[] getVariables(Expression[] params, final ExpressionContext context) {
    if (params.length != 0) return null;

    Project project = context.getProject();
    final int offset = context.getStartOffset();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final ArrayList array = new ArrayList();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);
    PsiVariable[] variables = MacroUtil.getVariablesVisibleAt(place, "");
    for(int i = 0; i < variables.length; i++){
      PsiType type = variables[i].getType();
      if (type instanceof PsiArrayType){
        array.add(variables[i]);
      }
    }
    return (PsiVariable[])array.toArray(new PsiVariable[array.size()]);
  }
}









