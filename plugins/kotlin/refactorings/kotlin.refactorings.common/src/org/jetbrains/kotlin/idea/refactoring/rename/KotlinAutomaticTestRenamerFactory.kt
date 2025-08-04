// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticTestRenamerFactory
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class KotlinAutomaticTestRenamerFactory : AutomaticTestRenamerFactory() {

    override fun isApplicable(element: PsiElement): Boolean {
        if (element is KtFile) {
            return element.declarations.none { it is KtClassOrObject && KotlinPsiBasedTestFramework.findTestFramework(it) != null }
        }
        val namedDeclaration = element as? KtNamedDeclaration ?: return false
        return KotlinPsiBasedTestFramework.findTestFramework(namedDeclaration) == null
    }

    override fun createRenamer(element: PsiElement, newName: String, usages: MutableCollection<UsageInfo>): AutomaticRenamer {
        val (oldClassName, newPsiClassName) = if (element is KtFile) {
            PackagePartClassUtils.getFilePartShortName(element.name) to PackagePartClassUtils.getFilePartShortName(newName)
        } else {
            (element as KtNamedDeclaration).name to newName
        }
        return TestsRenamer(element as PsiNamedElement, newPsiClassName, oldClassName)
    }
}