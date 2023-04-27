// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.junit.JUnitConfigurationProducer
import com.intellij.execution.junit.JUnitUtil.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.base.analysis.KotlinSafeAnalysisWrapper
import org.jetbrains.kotlin.idea.base.codeInsight.FrameworkAvailabilityChecker
import org.jetbrains.kotlin.idea.base.codeInsight.isFrameworkAvailable
import org.jetbrains.kotlin.idea.extensions.KotlinTestFrameworkProvider

class JunitKotlinTestFrameworkProvider : KotlinTestFrameworkProvider {
    companion object {
        @JvmStatic
        fun getInstance(): JunitKotlinTestFrameworkProvider {
            return KotlinTestFrameworkProvider.EP_NAME
                .findExtensionOrFail(JunitKotlinTestFrameworkProvider::class.java)
        }
    }

    override val canRunJvmTests: Boolean
        get() = true

    override fun isProducedByJava(configuration: ConfigurationFromContext): Boolean {
        return configuration.isProducedBy(JUnitConfigurationProducer::class.java)
    }

    override fun isProducedByKotlin(configuration: ConfigurationFromContext): Boolean {
        return configuration.isProducedBy(KotlinJUnitRunConfigurationProducer::class.java)
    }

    override fun isTestJavaClass(testClass: PsiClass): Boolean {
        return KotlinSafeAnalysisWrapper.runSafely(
            context = testClass,
            body = { isTestClass(testClass, false, true) },
            fallback = { false }
        )
    }

    override fun isTestJavaMethod(testMethod: PsiMethod): Boolean {
        return KotlinSafeAnalysisWrapper.runSafely(
            context = testMethod,
            body = { isTestMethod(PsiLocation.fromPsiElement(testMethod), false) },
            fallback = { false }
        )
    }

    override fun isTestFrameworkAvailable(element: PsiElement): Boolean {
        return isFrameworkAvailable<JUnitAvailabilityChecker>(element)
    }
}

@Service(Service.Level.PROJECT)
internal class JUnitAvailabilityChecker(project: Project) : FrameworkAvailabilityChecker(project) {
    override val fqNames: Set<String> = setOf(TEST_CASE_CLASS, TEST_ANNOTATION, CUSTOM_TESTABLE_ANNOTATION)

    override val javaClassLookup: Boolean = true
    override val aliasLookup: Boolean = false
    override val kotlinFullClassLookup: Boolean = false
}