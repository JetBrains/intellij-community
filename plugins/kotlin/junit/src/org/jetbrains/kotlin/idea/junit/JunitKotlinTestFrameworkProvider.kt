// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.junit.JUnitConfigurationProducer
import com.intellij.execution.junit.JUnitUtil
import com.intellij.execution.junit.JUnitUtil.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.core.util.FrameworkAvailabilityChecker
import org.jetbrains.kotlin.idea.core.util.isFrameworkAvailable
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