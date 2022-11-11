// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit.framework

import com.intellij.execution.junit.JUnitUtil
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.idea.testIntegration.framework.AbstractKotlinTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFramework.Companion.KOTLIN_TEST_TEST
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction

class JUnit5KotlinTestFramework : AbstractKotlinTestFramework() {
    private companion object {
        private val METHOD_ANNOTATION_FQN = setOf(
            JUnitUtil.TEST5_ANNOTATION,
            KOTLIN_TEST_TEST,
            "org.junit.jupiter.params.ParameterizedTest",
            "org.junit.jupiter.api.RepeatedTest",
            "org.junit.jupiter.api.TestFactory",
            "org.junit.jupiter.api.TestTemplate"
        )
    }

    override val markerClassFqn: String = JUnitUtil.TEST5_ANNOTATION
    override val disabledTestAnnotation: String = "org.junit.jupiter.api.Disabled"

    override fun isTestClass(declaration: KtClassOrObject): Boolean {
        return super.isTestClass(declaration) && CachedValuesManager.getCachedValue(declaration) {
            CachedValueProvider.Result.create(isJUnit5TestClass(declaration), PsiModificationTracker.MODIFICATION_COUNT)
        }
    }

    override fun isTestMethod(declaration: KtNamedFunction): Boolean {
        if (!super.isTestMethod(declaration)) return false
        if (declaration.annotationEntries.isEmpty()) return false
        return isJUnit5TestMethod(declaration)
    }

    private fun isJUnit5TestClass(declaration: KtClassOrObject): Boolean {
        if (declaration is KtClass && declaration.isInner() && !isAnnotated(declaration, "org.junit.jupiter.api.Nested")) {
            return false
        } else if (declaration.isTopLevel() && isAnnotated(declaration, "org.junit.jupiter.api.extension.ExtendWith")) {
            return true
        }

        return declaration.declarations
            .asSequence()
            .filterIsInstance<KtNamedFunction>()
            .any { isJUnit5TestMethod(it) }
    }

    private fun isJUnit5TestMethod(method: KtNamedFunction): Boolean {
        return isAnnotated(method, METHOD_ANNOTATION_FQN)
    }
}