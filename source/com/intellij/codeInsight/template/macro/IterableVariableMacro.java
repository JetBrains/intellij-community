package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;

/**
 * @author ven
 */
public class IterableVariableMacro extends VariableTypeMacroBase {
  public String getName() {
    return "iterableVariable";
  }

  public String getDescription() {
    return "iterableVariable()";
  }

  protected PsiVariable[] getVariables(Expression[] params, final ExpressionContext context) {
    if (params.length != 0) return null;

    Project project = context.getProject();
    final int offset = context.getStartOffset();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final ArrayList<PsiVariable> array = new ArrayList<PsiVariable>();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);
    PsiVariable[] variables = MacroUtil.getVariablesVisibleAt(place, "");
    PsiType iterableType = PsiManager.getInstance(project).getElementFactory().createTypeByFQClassName("java.lang.Iterable", file.getResolveScope());
    for (PsiVariable var : variables) {
      if (var.getParent() instanceof PsiForeachStatement
          && var.getParent() == PsiTreeUtil.getParentOfType(place, PsiForeachStatement.class)) {
        continue;
      }
      PsiType type = var.getType();
      if (type instanceof PsiArrayType || iterableType.isAssignableFrom(type)) {
        array.add(var);
      }
    }
    return array.toArray(new PsiVariable[array.size()]);
  }
}
