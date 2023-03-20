// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.testng

import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiClassUtil
import com.theoryinpractice.testng.configuration.TestNGConfigurationProducer
import com.theoryinpractice.testng.util.TestNGUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.codeInsight.FrameworkAvailabilityChecker
import org.jetbrains.kotlin.idea.base.codeInsight.isFrameworkAvailable
import org.jetbrains.kotlin.idea.extensions.KotlinTestFrameworkProvider

@ApiStatus.Internal
class TestNgKotlinTestFrameworkProvider : KotlinTestFrameworkProvider {
    companion object {
        @JvmStatic
        fun getInstance(): TestNgKotlinTestFrameworkProvider {
            return KotlinTestFrameworkProvider.EP_NAME
                .findExtensionOrFail(TestNgKotlinTestFrameworkProvider::class.java)
        }
    }

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

    override fun isTestFrameworkAvailable(element: PsiElement): Boolean {
        return isFrameworkAvailable<TestNGAvailabilityChecker>(element)
    }
}

@Service(Service.Level.PROJECT)
class TestNGAvailabilityChecker(project: Project) : FrameworkAvailabilityChecker(project) {
    override val fqNames: Set<String> = setOf(TestNGUtil.TEST_ANNOTATION_FQN)

    override val javaClassLookup: Boolean = true
    override val aliasLookup: Boolean = false
    override val kotlinFullClassLookup: Boolean = false
}