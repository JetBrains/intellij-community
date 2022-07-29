// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit.framework

import com.intellij.execution.junit.JUnitUtil
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.idea.testIntegration.framework.AbstractKotlinTestFramework
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class JUnit3KotlinTestFramework : AbstractKotlinTestFramework() {
    private companion object {
        private val CLASS_ANNOTATION_FQN = setOf(JUnitUtil.TEST_CASE_CLASS)
    }

    override val markerClassFqn: String
        get() = JUnitUtil.TEST_CASE_CLASS

    override val disabledTestAnnotation: String
        get() = "org.junit.Ignore"

    override fun isTestClass(declaration: KtClassOrObject): Boolean {
        return super.isTestClass(declaration) && CachedValuesManager.getCachedValue(declaration) {
            CachedValueProvider.Result.create(isJUnit3TestClass(declaration, mutableSetOf()), PsiModificationTracker.MODIFICATION_COUNT)
        }
    }

    override fun isTestMethod(declaration: KtNamedFunction): Boolean {
        if (!super.isTestMethod(declaration)) return false
        if (declaration.name?.startsWith("test") != true) return false
        val classOrObject = declaration.getParentOfType<KtClassOrObject>(true) ?: return false
        if (classOrObject is KtClass && classOrObject.isInner()) return false

        return isTestClass(classOrObject)
    }

    private fun isJUnit3TestClass(declaration: KtClassOrObject, visitedShortNames: MutableSet<String>): Boolean {
        if (declaration is KtClass && declaration.isInner()) return false
        for (superTypeEntry in declaration.superTypeListEntries) {
            if (superTypeEntry is KtSuperTypeCallEntry && superTypeEntry.valueArguments.isEmpty()) {
                val containingFile = declaration.containingKtFile
                val superShortName = superTypeEntry.calleeExpression.text

                // circular dependency detected
                if (!visitedShortNames.add(superShortName)) return false

                if (checkNameMatch(containingFile, CLASS_ANNOTATION_FQN, superShortName)) {
                    return true
                }

                containingFile.declarations.firstOrNull { it is KtClassOrObject && it.name == superShortName }?.let {
                    return isJUnit3TestClass(it as KtClassOrObject, visitedShortNames)
                }
            }
        }
        return false
    }
}