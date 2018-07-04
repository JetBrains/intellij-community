/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.PsiNamedElementUsageGroupBase;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.impl.FileStructureGroupRuleProvider;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRule;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyFunctionGroupingRuleProvider implements FileStructureGroupRuleProvider {
  public UsageGroupingRule getUsageGroupingRule(@NotNull Project project) {
    return new PyFunctionGroupingRule();
  }

  private static class PyFunctionGroupingRule extends SingleParentUsageGroupingRule {
    @Nullable
    @Override
    protected UsageGroup getParentGroupFor(@NotNull Usage usage, @NotNull UsageTarget[] targets) {
      if (!(usage instanceof PsiElementUsage)) return null;
      final PsiElement psiElement = ((PsiElementUsage)usage).getElement();
      final PyFunction pyFunction = PsiTreeUtil.getParentOfType(psiElement, PyFunction.class, false, PyClass.class);
      if (pyFunction != null) {
        return new PsiNamedElementUsageGroupBase<>(pyFunction);
      }
      return null;
    }
  }
}