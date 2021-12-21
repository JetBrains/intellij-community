// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.junit

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.junit.JUnitConfigurationProducer
import com.intellij.execution.junit.JUnitUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
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
}