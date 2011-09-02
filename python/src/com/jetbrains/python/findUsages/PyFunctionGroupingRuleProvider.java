package com.jetbrains.python.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.PsiNamedElementUsageGroupBase;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.impl.FileStructureGroupRuleProvider;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageGroupingRule;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyFunctionGroupingRuleProvider implements FileStructureGroupRuleProvider {
  public UsageGroupingRule getUsageGroupingRule(Project project) {
    return new PyFunctionGroupingRule();
  }

  private static class PyFunctionGroupingRule implements UsageGroupingRule {
    public UsageGroup groupUsage(@NotNull Usage usage) {
      if (!(usage instanceof PsiElementUsage)) return null;
      final PsiElement psiElement = ((PsiElementUsage)usage).getElement();
      final PyFunction pyFunction = PsiTreeUtil.getParentOfType(psiElement, PyFunction.class, false, PyClass.class);
      if (pyFunction != null) {
        return new PsiNamedElementUsageGroupBase<PyFunction>(pyFunction);
      }
      return null;
    }
  }
}