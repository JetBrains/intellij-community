// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.testng

import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import com.intellij.psi.util.PsiClassUtil
import com.theoryinpractice.testng.configuration.TestNGConfigurationProducer
import com.theoryinpractice.testng.util.TestNGUtil
import org.jetbrains.kotlin.idea.core.util.AvailabilityProvider
import org.jetbrains.kotlin.idea.core.util.isClassAvailableInModule
import org.jetbrains.kotlin.idea.extensions.KotlinTestFrameworkProvider

object TestNgKotlinTestFrameworkProvider : KotlinTestFrameworkProvider {
    override val canRunJvmTests: Boolean
        get() = true

    override fun isProducedByJava(configuration: ConfigurationFromContext): Boolean {
        return configuration.isProducedBy(TestNGConfigurationProducer::class.java)
    }

    override fun isProducedByKotlin(configuration: ConfigurationFromContext): Boolean {
        return configuration.isProducedBy(KotlinTestNgConfigurationProducer::class.java)
    }

    override fun isTestJavaClass(testClass: PsiClass): Boolean {
        return PsiClassUtil.isRunnableClass(testClass, true, false) && TestNGUtil.hasTest(testClass)
    }

    override fun isTestJavaMethod(testMethod: PsiMethod): Boolean {
        return TestNGUtil.hasTest(testMethod)
    }

    override fun isTestFrameworkAvailable(element: PsiElement): Boolean =
        element.isClassAvailableInModule(
            TESTNG_AVAILABLE_KEY,
            TESTNG_AVAILABLE_PROVIDER_WITHOUT_TEST_SCOPE,
            TESTNG_AVAILABLE_PROVIDER_WITH_TEST_SCOPE
        ) ?: false
}

private val TESTNG_AVAILABLE_KEY = Key<ParameterizedCachedValue<Boolean, Module>>("TESTNG_AVAILABLE")
private val TESTNG_AVAILABLE_PROVIDER_WITHOUT_TEST_SCOPE: ParameterizedCachedValueProvider<Boolean, Module> = TestNGAvailabilityProvider(test = false)
private val TESTNG_AVAILABLE_PROVIDER_WITH_TEST_SCOPE = TestNGAvailabilityProvider(true)

private class TestNGAvailabilityProvider(test: Boolean) : AvailabilityProvider(
    test,
    fqNames = setOf(TestNGUtil.TEST_ANNOTATION_FQN),
    javaClassLookup = true,
    aliasLookup = false,
    kotlinFullClassLookup = false
)
