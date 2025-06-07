// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.searching.usages

import com.intellij.openapi.project.Project
import com.intellij.usages.*
import com.intellij.usages.impl.FileStructureGroupRuleProvider
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.SingleParentUsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRule
import org.jetbrains.kotlin.idea.projectView.KtDeclarationTreeNode.Companion.tryGetRepresentableText
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class KotlinDeclarationGroupingRule(val level: Int = 0) : SingleParentUsageGroupingRule() {
    override fun getParentGroupFor(usage: Usage, targets: Array<UsageTarget>): UsageGroup? {
        var element = usage.safeAs<PsiElementUsage>()?.element ?: return null

        if (element.containingFile !is KtFile) return null

        if (element is KtFile) {
            val offset = usage.safeAs<UsageInfo2UsageAdapter>()?.usageInfo?.navigationOffset
            if (offset != null) {
                element = element.findElementAt(offset) ?: element
            }
        }

        val parentList =
            element.parents.filterIsInstance<KtNamedDeclaration>().filterNot {
            it is KtProperty && it.isLocal || (it is KtScript)
        }.toList()
        if (parentList.size <= level) {
            return null
        }

        val declaration = parentList[parentList.size - level - 1]
        val name = tryGetRepresentableText(declaration, renderReceiverType = false, renderArguments = false, renderReturnType = false)
        return PsiNamedElementUsageGroupBase(declaration, name)
    }
}

private class KotlinDeclarationGroupRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule = KotlinDeclarationGroupingRule(0)
}

private class KotlinDeclarationSecondLevelGroupRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule = KotlinDeclarationGroupingRule(1)
}
