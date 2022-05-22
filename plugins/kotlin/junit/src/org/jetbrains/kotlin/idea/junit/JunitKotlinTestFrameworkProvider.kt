// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.junit.JUnitConfigurationProducer
import com.intellij.execution.junit.JUnitUtil
import com.intellij.execution.junit.JUnitUtil.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import org.jetbrains.kotlin.idea.core.util.AvailabilityProvider
import org.jetbrains.kotlin.idea.core.util.isClassAvailableInModule
import org.jetbrains.kotlin.idea.extensions.KotlinTestFrameworkProvider
import org.jetbrains.kotlin.idea.util.actionUnderSafeAnalyzeBlock

object JunitKotlinTestFrameworkProvider : KotlinTestFrameworkProvider {
    override val canRunJvmTests: Boolean
        get() = true

    override fun isProducedByJava(configuration: ConfigurationFromContext): Boolean {
        return configuration.isProducedBy(JUnitConfigurationProducer::class.java)
    }

    override fun isProducedByKotlin(configuration: ConfigurationFromContext): Boolean {
        return configuration.isProducedBy(KotlinJUnitRunConfigurationProducer::class.java)
    }

    override fun isTestJavaClass(testClass: PsiClass): Boolean {
        return testClass.actionUnderSafeAnalyzeBlock({ JUnitUtil.isTestClass(testClass, false, true) }, { false })
    }

    override fun isTestJavaMethod(testMethod: PsiMethod): Boolean {
        return testMethod.actionUnderSafeAnalyzeBlock({ JUnitUtil.isTestMethod(PsiLocation.fromPsiElement(testMethod), false) }, { false })
    }

    override fun isTestFrameworkAvailable(element: PsiElement): Boolean {
        val b = element.isClassAvailableInModule(
            JUNIT_AVAILABLE_KEY,
            JUNIT_AVAILABLE_PROVIDER_WITHOUT_TEST_SCOPE,
            JUNIT_AVAILABLE_PROVIDER_WITH_TEST_SCOPE
        ) ?: false
        return b
    }
}


private val JUNIT_AVAILABLE_KEY = Key<ParameterizedCachedValue<Boolean, Module>>("JUNIT_AVAILABLE")
private val JUNIT_AVAILABLE_PROVIDER_WITHOUT_TEST_SCOPE: ParameterizedCachedValueProvider<Boolean, Module> = JUnitAvailabilityProvider(test = false)
private val JUNIT_AVAILABLE_PROVIDER_WITH_TEST_SCOPE = JUnitAvailabilityProvider(true)

private class JUnitAvailabilityProvider(test: Boolean) : AvailabilityProvider(
    test,
    fqNames = setOf(TEST_CASE_CLASS, TEST_ANNOTATION, CUSTOM_TESTABLE_ANNOTATION),
    javaClassLookup = true,
    aliasLookup = false,
    kotlinFullClassLookup = false
)
