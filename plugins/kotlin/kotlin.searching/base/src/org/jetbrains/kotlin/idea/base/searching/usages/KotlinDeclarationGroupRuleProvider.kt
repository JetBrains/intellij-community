// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.searching.usages

import com.intellij.openapi.project.Project
import com.intellij.usages.*
import com.intellij.usages.impl.FileStructureGroupRuleProvider
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.SingleParentUsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRule
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinDeclarationGroupingRule(val level: Int = 0) : SingleParentUsageGroupingRule() {
    override fun getParentGroupFor(usage: Usage, targets: Array<UsageTarget>): UsageGroup? {
        var element = usage.safeAs<PsiElementUsage>()?.element ?: return null

        if (element.containingFile !is KtFile) return null

        if (element is KtFile) {
            val offset = usage.safeAs<UsageInfo2UsageAdapter>()?.usageInfo?.navigationOffset
            if (offset != null) {
                element = element.findElementAt(offset) ?: element
            }
        }

        val parentList = element.parents.filterIsInstance<KtNamedDeclaration>().filterNot { it is KtProperty && it.isLocal }.toList()
        if (parentList.size <= level) {
            return null
        }

        return PsiNamedElementUsageGroupBase(parentList[parentList.size - level - 1])
    }
}

class KotlinDeclarationGroupRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule = KotlinDeclarationGroupingRule(0)
}

class KotlinDeclarationSecondLevelGroupRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule = KotlinDeclarationGroupingRule(1)
}
