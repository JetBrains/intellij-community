// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit

import com.intellij.execution.junit.JUnit5Framework
import com.intellij.execution.junit.JUnitUtil
import com.intellij.lang.Language
import com.intellij.lang.OuterModelsModificationTrackerManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.testIntegration.framework.AbstractKotlinPsiBasedTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtClassOrObject
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtNamedFunction
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinJUnit5Framework: JUnit5Framework(), KotlinPsiBasedTestFramework {
    private val psiBasedDelegate = object : AbstractKotlinPsiBasedTestFramework() {

        override val markerClassFqn: String = JUnitUtil.TEST5_ANNOTATION
        override val disabledTestAnnotation: String = "org.junit.jupiter.api.Disabled"

        override fun isTestClass(declaration: KtClassOrObject): Boolean =
            super.isTestClass(declaration) && CachedValuesManager.getCachedValue(declaration) {
                CachedValueProvider.Result.create(
                    isJUnit5TestClass(declaration),
                    OuterModelsModificationTrackerManager.getInstance(declaration.project).tracker
                )
            }

        override fun isTestMethod(declaration: KtNamedFunction): Boolean {
            if (!super.isTestMethod(declaration)) return false
            if (declaration.annotationEntries.isEmpty()) return false
            return isJUnit5TestMethod(declaration)
        }

        private fun isJUnit5TestClass(declaration: KtClassOrObject): Boolean {
            val b = if (!isFrameworkAvailable(declaration)) {
                false
            } else if (declaration is KtClass && declaration.isInner() && !isAnnotated(declaration, "org.junit.jupiter.api.Nested")) {
                false
            } else if (declaration.isTopLevel() && isAnnotated(declaration, "org.junit.jupiter.api.extension.ExtendWith")) {
                true
            } else {
                findAnnotatedFunction(declaration, METHOD_ANNOTATION_FQN) != null
            }
            return b
        }

        private fun isJUnit5TestMethod(method: KtNamedFunction): Boolean {
            return isAnnotated(method, METHOD_ANNOTATION_FQN)
        }

        override fun findSetUp(classOrObject: KtClassOrObject): KtNamedFunction? =
            findAnnotatedFunction(classOrObject.takeIf { isTestClass(it) }, setUpAnnotations)

        override fun findTearDown(classOrObject: KtClassOrObject): KtNamedFunction? =
            findAnnotatedFunction(classOrObject.takeIf { isTestClass(it) }, tearDownAnnotations)

    }

    override fun responsibleFor(declaration: KtNamedDeclaration): Boolean =
        psiBasedDelegate.responsibleFor(declaration)

    override fun isTestClass(clazz: PsiElement): Boolean {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return false
        return psiBasedDelegate.isTestClass(ktClassOrObject)
    }

    override fun findSetUpMethod(clazz: PsiElement): PsiElement? {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return null
        return psiBasedDelegate.findSetUp(ktClassOrObject)
    }

    override fun findTearDownMethod(clazz: PsiElement): PsiElement? {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return null
        return psiBasedDelegate.findTearDown(ktClassOrObject)
    }

    override fun isIgnoredMethod(element: PsiElement?): Boolean =
        element.asKtNamedFunction()?.let(psiBasedDelegate::isIgnoredMethod) ?: false

    override fun isTestMethod(element: PsiElement?): Boolean =
        element.asKtNamedFunction()?.let(psiBasedDelegate::isTestMethod) ?: false

    override fun getLanguage(): Language = KotlinLanguage.INSTANCE

    override fun isTestClass(declaration: KtClassOrObject): Boolean =
        psiBasedDelegate.isTestClass(declaration)

    override fun isTestMethod(declaration: KtNamedFunction): Boolean =
        psiBasedDelegate.isTestMethod(declaration)

    override fun isIgnoredMethod(declaration: KtNamedFunction): Boolean =
        psiBasedDelegate.isIgnoredMethod(declaration)

    companion object {
        private val METHOD_ANNOTATION_FQN = setOf(
            JUnitUtil.TEST5_ANNOTATION,
            KotlinPsiBasedTestFramework.KOTLIN_TEST_TEST,
            "org.junit.jupiter.params.ParameterizedTest",
            "org.junit.jupiter.api.RepeatedTest",
            "org.junit.jupiter.api.TestFactory",
            "org.junit.jupiter.api.TestTemplate"
        )

        private val setUpAnnotations = setOf(JUnitUtil.BEFORE_EACH_ANNOTATION_NAME, KotlinPsiBasedTestFramework.KOTLIN_TEST_BEFORE_TEST)
        private val tearDownAnnotations = setOf(JUnitUtil.AFTER_EACH_ANNOTATION_NAME, KotlinPsiBasedTestFramework.KOTLIN_TEST_AFTER_TEST)
    }
}

