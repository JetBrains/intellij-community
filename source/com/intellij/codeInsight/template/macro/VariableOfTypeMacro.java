package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class VariableOfTypeMacro implements Macro {

  public String getName() {
    return "variableOfType";
  }

  public String getDescription() {
    return "variableOfType(Type)";
  }

  public String getDefaultValue() {
    return "a";
  }

  public Result calculateResult(Expression[] params, ExpressionContext context) {
    final PsiElement[] vars = getVariables(params, context);
    if (vars == null || vars.length == 0) return null;
    return new PsiElementResult(vars[0]);
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    return null;
  }

  public LookupItem[] calculateLookupItems(Expression[] params, final ExpressionContext context) {
    final PsiElement[] vars = getVariables(params, context);
    if (vars == null || vars.length < 2) return null;
    final LinkedHashSet set = new LinkedHashSet();
    for(int i = 0; i < vars.length; i++){
      LookupItemUtil.addLookupItem(set, vars[i], "");
    }
    return (LookupItem[])set.toArray(new LookupItem[set.size()]);
  }

  private PsiElement[] getVariables(Expression[] params, final ExpressionContext context) {
    if (params.length != 1) return null;
    final Result result = params[0].calculateResult(context);
    if (result == null) return null;

    Project project = context.getProject();
    final int offset = context.getStartOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final ArrayList array = new ArrayList();
    PsiType type = MacroUtil.resultToPsiType(result, context);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);

    PsiVariable[] variables = MacroUtil.getVariablesVisibleAt(place, "");
    PsiManager manager = PsiManager.getInstance(project);
    for(int i = 0; i < variables.length; i++){
      PsiVariable var = variables[i];

      if (var instanceof PsiField && ((PsiField)var).hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass varClass = ((PsiField)var).getContainingClass();
        PsiClass placeClass = PsiTreeUtil.getParentOfType(place, PsiClass.class);
        if (!manager.areElementsEquivalent(varClass, placeClass)) continue;
      } else if (var instanceof PsiLocalVariable) {
        if (var.getParent() instanceof PsiDeclarationStatement && var.getParent().getTextRange().contains(offset)) {
          continue;
        }
      }

      PsiType type1 = var.getType();
      if (type == null || type.isAssignableFrom(type1)){
        array.add(variables[i]);
      }
    }

    PsiExpression[] expressions = MacroUtil.getStandardExpressions(place);
    for(int i = 0; i < expressions.length; i++){
      PsiExpression expr = expressions[i];
      PsiType type1 = expr.getType();
      if (type == null || type1 != null && type.isAssignableFrom(type1)){
        array.add(expr);
      }
    }
    return (PsiElement[])array.toArray(new PsiElement[array.size()]);
  }
}
