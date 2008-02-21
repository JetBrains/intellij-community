package com.intellij.usages.impl.rules;

import com.intellij.usages.impl.FileStructureGroupRuleProvider;
import com.intellij.usages.rules.UsageGroupingRule;

/**
 * @author yole
 */
public class JavaClassGroupRuleProvider implements FileStructureGroupRuleProvider {
  public UsageGroupingRule getUsageGroupingRule() {
    return new ClassGroupingRule();
  }
}
