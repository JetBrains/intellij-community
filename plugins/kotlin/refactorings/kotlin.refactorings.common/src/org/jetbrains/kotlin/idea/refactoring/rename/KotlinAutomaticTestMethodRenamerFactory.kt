// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticTestMethodRenamerFactory
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class KotlinAutomaticTestMethodRenamerFactory : AutomaticTestMethodRenamerFactory() {
    override fun isApplicable(element: PsiElement): Boolean {
        if (element !is KtNamedFunction) return false
        return super.isApplicable(element)
    }

    override fun createRenamer(element: PsiElement, newName: String, usages: MutableCollection<UsageInfo>): AutomaticRenamer {
        val ktFunction = element as? KtNamedFunction
        val psiClass = ktFunction?.containingClassOrObject
        val module = element.module
        return AutomaticTestMethodRenamer(
            ktFunction?.name,
            psiClass?.name,
            module,
            newName)
    }

    override fun isEnabled(): Boolean = KotlinCommonRefactoringSettings.getInstance().renameTestMethods

    override fun setEnabled(enabled: Boolean) {
        KotlinCommonRefactoringSettings.getInstance().renameTestMethods = enabled
    }
}