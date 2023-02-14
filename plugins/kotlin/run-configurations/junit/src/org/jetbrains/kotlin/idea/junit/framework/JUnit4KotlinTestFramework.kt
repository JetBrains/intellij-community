// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit.framework

import com.intellij.execution.junit.JUnitUtil
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.kotlin.idea.testIntegration.framework.AbstractKotlinTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFramework.Companion.KOTLIN_TEST_TEST
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class JUnit4KotlinTestFramework : AbstractKotlinTestFramework() {
    override val markerClassFqn: String = JUnitUtil.TEST_ANNOTATION
    override val disabledTestAnnotation: String = "org.junit.Ignore"

    override fun isTestClass(declaration: KtClassOrObject): Boolean {
        return super.isTestClass(declaration) && CachedValuesManager.getCachedValue(declaration) {
            CachedValueProvider.Result.create(isJUnit4TestClass(declaration), PsiModificationTracker.MODIFICATION_COUNT)
        }
    }

    override fun isTestMethod(declaration: KtNamedFunction): Boolean {
        return when {
            !super.isTestMethod(declaration) -> false
            declaration.annotationEntries.isEmpty() -> false
            else -> isJUnit4TestMethod(declaration)
        }
    }

    private fun isJUnit4TestMethod(declaration: KtNamedFunction): Boolean {
        return isAnnotated(declaration, setOf(JUnitCommonClassNames.ORG_JUNIT_TEST, KOTLIN_TEST_TEST))
    }

    private fun isJUnit4TestClass(declaration: KtClassOrObject): Boolean {
        if (declaration.safeAs<KtClass>()?.isInner() == true) {
            return false
        } else if (declaration.isTopLevel() && isAnnotated(declaration, JUnitUtil.RUN_WITH)) {
            return true
        }

        return declaration.declarations
            .asSequence()
            .filterIsInstance<KtNamedFunction>()
            .any { isJUnit4TestMethod(it) }
    }
}