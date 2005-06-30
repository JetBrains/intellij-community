package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.psi.PsiVariable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author ven
 */
public abstract class VariableTypeMacroBase implements Macro {
  protected abstract PsiVariable[] getVariables(Expression[] params, final ExpressionContext context);

  public LookupItem[] calculateLookupItems(Expression[] params, final ExpressionContext context) {
    final PsiVariable[] vars = getVariables(params, context);
    if (vars == null || vars.length < 2) return null;
    Set<LookupItem> set = new LinkedHashSet<LookupItem>();
    for (PsiVariable var : vars) {
      LookupItemUtil.addLookupItem(set, var, "");
    }
    return set.toArray(new LookupItem[set.size()]);
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
