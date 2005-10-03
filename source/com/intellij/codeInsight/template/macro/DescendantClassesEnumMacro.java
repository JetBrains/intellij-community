package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiSearchHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DescendantClassesEnumMacro implements Macro{
  public String getName() {
    return "descendantClassesEnum";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.descendant.classes.enum");
  }

  public String getDefaultValue() {
    return "";
  }

  public Result calculateResult(Expression[] params, ExpressionContext context) {
    final List<PsiClass> classes = findDescendants(context, params);
    if (classes == null || classes.size() == 0) return null;
    Result[] results = calculateResults(classes);

    return results[0];
  }

  private Result[] calculateResults(final List<PsiClass> classes) {
    Result[] results = new Result[classes.size()];
    int i = 0;

    for (final PsiClass aClass : classes) {
      results[i++] = new PsiElementResult(aClass);
    }
    return results;
  }

  private List<PsiClass> findDescendants(ExpressionContext context, Expression[] params) {
    if (params == null || params.length == 0) return null;
    PsiManager instance = PsiManager.getInstance(context.getProject());

    final PsiClass myBaseClass = instance.findClass(
      params[0].calculateResult(context).toString(),
      GlobalSearchScope.allScope(context.getProject())
    );

    if (myBaseClass!=null) {
      PsiSearchHelper helper = instance.getSearchHelper();

      final List<PsiClass> classes = new ArrayList<PsiClass>();

      helper.processInheritors(new PsiElementProcessor<PsiClass>() {
        public boolean execute(PsiClass element) {
          classes.add(element);
          return true;
        }

      }, myBaseClass, GlobalSearchScope.projectScope(context.getProject()), true, true);

      return classes;
    }

    return null;
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    final List<PsiClass> classes = findDescendants(context, params);
    if (classes == null || classes.size() == 0) return null;
    Result[] results = calculateResults(classes);

    return results[0];
  }

  public LookupItem[] calculateLookupItems(Expression[] params, ExpressionContext context) {
    final List<PsiClass> classes = findDescendants(context, params);
    if (classes == null || classes.size() == 0) return null;

    Set<LookupItem> set = new LinkedHashSet<LookupItem>();
    boolean isFQN = params.length > 1 && Boolean.valueOf(params[1].calculateResult(context).toString());

    for (PsiClass object : classes) {
      LookupItemUtil.addLookupItem(set, isFQN ? object.getQualifiedName() : object.getName(), "");
    }

    return set.toArray(new LookupItem[set.size()]);
  }

}