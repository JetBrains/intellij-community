// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PyClassGroupingRuleProvider implements FileStructureGroupRuleProvider {
  @Override
  public UsageGroupingRule getUsageGroupingRule(@NotNull Project project) {
    return new PyClassGroupingRule();
  }

  private static class PyClassGroupingRule extends SingleParentUsageGroupingRule {
    @Nullable
    @Override
    protected UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
      if (!(usage instanceof PsiElementUsage)) return null;
      final PsiElement psiElement = ((PsiElementUsage)usage).getElement();
      final PyClass pyClass = PsiTreeUtil.getParentOfType(psiElement, PyClass.class);
      if (pyClass != null) {
        return new PsiNamedElementUsageGroupBase<>(pyClass);
      }
      return null;
    }
  }
}
