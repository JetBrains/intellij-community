package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.psi.PsiVariable;

import java.util.LinkedHashSet;

/**
 * @author ven
 */
public abstract class VariableTypeMacroBase implements Macro {
  protected abstract PsiVariable[] getVariables(Expression[] params, final ExpressionContext context);

  public LookupItem[] calculateLookupItems(Expression[] params, final ExpressionContext context) {
    final PsiVariable[] vars = getVariables(params, context);
    if (vars == null || vars.length < 2) return null;
    LinkedHashSet set = new LinkedHashSet();
    for(int i = 0; i < vars.length; i++){
      LookupItemUtil.addLookupItem(set, vars[i], "");
    }
    return (LookupItem[]) set.toArray(new LookupItem[set.size()]);
  }

  public Result calculateResult(Expression[] params, ExpressionContext context) {
    final PsiVariable[] vars = getVariables(params, context);
    if (vars == null || vars.length == 0) return null;
    return new PsiElementResult(vars[0]);
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    return null;
  }

  public String getDefaultValue() {
    return "a";
  }

}
