// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.runConfigurations.jvm

import com.intellij.codeInsight.TestFrameworks
import com.intellij.testIntegration.TestFramework
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFramework
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Fallback implementation of [KotlinTestFramework] delegating its calls to [com.intellij.testIntegration.TestFramework]
 * Should be used only in case when none of other (optimized) implementations match.
 */
class KotlinDelegatingTestFramework : KotlinTestFramework {
    override val isSlow: Boolean = true

    override fun responsibleFor(declaration: KtNamedDeclaration): Boolean {
        return when (declaration) {
            is KtClassOrObject -> return isTestClass(declaration)
            is KtNamedFunction -> return isTestMethod(declaration)
            else -> false
        }
    }

    override fun isTestClass(declaration: KtClassOrObject): Boolean {
        val lightClass = declaration.toLightClass() ?: return false
        val framework = TestFrameworks.detectFramework(lightClass) ?: return false
        return framework.isTestClass(lightClass)
    }

    override fun isTestMethod(declaration: KtNamedFunction): Boolean {
        val framework = detectFramework(declaration) ?: return false
        val lightMethod = declaration.toLightMethods().firstOrNull() ?: return false
        return framework.isTestMethod(lightMethod, false)
    }

    override fun isIgnoredMethod(declaration: KtNamedFunction): Boolean {
        val framework = detectFramework(declaration) ?: return false
        val lightMethod = declaration.toLightMethods().firstOrNull() ?: return false
        return framework.isIgnoredMethod(lightMethod)
    }

    override fun qualifiedName(declaration: KtNamedDeclaration): String?  = when (declaration) {
        is KtClassOrObject -> declaration.toLightClass()?.qualifiedName
        is KtNamedFunction -> {
            val lightMethod = declaration.toLightMethods().firstOrNull()
            lightMethod?.containingClass.safeAs<KtLightClass>()?.qualifiedName
        }
        else -> null
    }

    private fun detectFramework(function: KtNamedFunction): TestFramework? {
        val lightMethod = function.toLightMethods().firstOrNull() ?: return null
        val lightClass = lightMethod.containingClass.safeAs<KtLightClass>() ?: return null
        return TestFrameworks.detectFramework(lightClass)
    }
}