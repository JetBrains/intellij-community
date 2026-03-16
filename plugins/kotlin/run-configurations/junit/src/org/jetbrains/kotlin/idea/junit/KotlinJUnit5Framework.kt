// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit

import com.intellij.execution.junit.JUnit5Framework
import com.intellij.execution.junit.JUnitUtil
import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.java.analysis.OuterModelsModificationTrackerManager
import com.intellij.lang.Language
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentOfType
import com.intellij.util.ThreeState
import com.intellij.util.ThreeState.NO
import com.intellij.util.ThreeState.UNSURE
import com.intellij.util.ThreeState.YES
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_DISABLED
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_NESTED
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_REPEATED_TEST
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_FACTORY
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_PLATFORM_SUITE_API_AFTERSUITE
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_PLATFORM_SUITE_API_BEFORESUITE
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.testIntegration.framework.AbstractKotlinPsiBasedTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtClassOrObject
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtNamedFunction
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Shared PSI-based delegate for Kotlin JUnit5-like frameworks (JUnit5 and JUnit6).
 */
internal class KotlinJUnit5PsiDelegate(
    markerClassFqnsParam: Collection<String>,
    private val isUnderTestSources: (KtClassOrObject) -> Boolean,
    private val isKotlinTestClass: (KtClassOrObject) -> Boolean,
) : AbstractKotlinPsiBasedTestFramework() {
    override val markerClassFqns: Collection<String> = markerClassFqnsParam

    override val disabledTestAnnotation: String = ORG_JUNIT_JUPITER_API_DISABLED

    override val allowTestMethodsInObject: Boolean = true

    override fun checkTestClass(declaration: KtClassOrObject): ThreeState {
        val checkState = super.checkTestClass(declaration)
        if (checkState != UNSURE) return checkState
        return CachedValuesManager.getCachedValue(declaration) {
            CachedValueProvider.Result.create(
                checkJUnit5TestClass(declaration),
                OuterModelsModificationTrackerManager.getTracker(declaration.project)
            )
        }
    }

    fun isPotentialTestClass(element: PsiElement): Boolean {
        if (element.language != KotlinLanguage.INSTANCE) return false
        val psiElement = (element as? KtLightElement<*, *>)?.kotlinOrigin ?: element
        val ktClassOrObject = psiElement.parentOfType<KtClassOrObject>(true) ?: return false

        return CachedValuesManager.getCachedValue(ktClassOrObject) {
            CachedValueProvider.Result.create(
                checkJUnit5PotentialTestClass(ktClassOrObject) != NO,
                OuterModelsModificationTrackerManager.getTracker(ktClassOrObject.project)
            )
        }
    }

    override fun isTestMethod(declaration: KtNamedFunction): Boolean {
        if (!super.isTestMethod(declaration)) return false
        if (declaration.annotationEntries.isEmpty()) return false
        return isJUnit5TestMethod(declaration)
    }

    private fun checkJUnit5TestClass(declaration: KtClassOrObject): ThreeState =
        if (!isFrameworkAvailable(declaration)) {
            NO
        } else {
            checkIsJUnit5TestClass(declaration, isPotential = false)
        }

    private fun checkJUnit5PotentialTestClass(declaration: KtClassOrObject): ThreeState =
        if (!isFrameworkAvailable(declaration) && !isFrameworkAvailable(declaration, KotlinPsiBasedTestFramework.KOTLIN_TEST_TEST, false)) {
            NO
        } else {
            checkIsJUnit5TestClass(declaration, isPotential = true)
        }

    private fun checkIsJUnit5TestClass(declaration: KtClassOrObject, isPotential: Boolean): ThreeState =
        if (isPotential) {
            if (isUnderTestSources(declaration)) UNSURE else NO
        } else if (!isFrameworkAvailable(declaration)) {
            NO
        } else if (declaration is KtClass && declaration.isInner()) {
            if (isAnnotated(declaration, ORG_JUNIT_JUPITER_API_NESTED)) YES else NO
        } else if (declaration.isTopLevel() && isAnnotated(declaration, ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH)) {
            YES
        } else if (findAnnotatedFunction(declaration, testableAnnotations) != null) {
            YES
        } else {
            UNSURE
        }

    private fun isJUnit5TestMethod(method: KtNamedFunction): Boolean {
        return isAnnotated(method, METHOD_ANNOTATION_FQN)
    }

    override fun findSetUp(classOrObject: KtClassOrObject): KtNamedFunction? =
        findAnnotatedFunction(classOrObject.takeIf { isKotlinTestClass(it) }, setUpAnnotations)

    override fun findTearDown(classOrObject: KtClassOrObject): KtNamedFunction? =
        findAnnotatedFunction(classOrObject.takeIf { isKotlinTestClass(it) }, tearDownAnnotations)

    fun findBeforeSuite(classOrObject: KtClassOrObject): KtNamedFunction? =
        findAnnotatedFunction(classOrObject.takeIf { isKotlinTestClass(it) }, setOf(ORG_JUNIT_PLATFORM_SUITE_API_BEFORESUITE))

    fun findAfterSuite(classOrObject: KtClassOrObject): KtNamedFunction? =
        findAnnotatedFunction(classOrObject.takeIf { isKotlinTestClass(it) }, setOf(ORG_JUNIT_PLATFORM_SUITE_API_AFTERSUITE))
}

class KotlinJUnit5Framework : JUnit5Framework(), KotlinPsiBasedTestFramework {
    private val psiBasedDelegate = KotlinJUnit5PsiDelegate(
        markerClassFqnsParam = markerClassFQNames,
        isUnderTestSources = this::isUnderTestSources,
        isKotlinTestClass = this::isTestClass
    )

    override fun responsibleFor(declaration: KtNamedDeclaration): Boolean =
        psiBasedDelegate.responsibleFor(declaration)

    override fun checkTestClass(declaration: KtClassOrObject): ThreeState = psiBasedDelegate.checkTestClass(declaration)

    override fun isTestClass(clazz: PsiElement): Boolean =
        when (val checkTestClass = checkTestClass(clazz)) {
            UNSURE -> super.isTestClass(clazz)
            else -> checkTestClass == YES
        }

    override fun isPotentialTestClass(clazz: PsiElement): Boolean =
        isTestClass(clazz) || psiBasedDelegate.isPotentialTestClass(clazz)

    override fun findSetUpMethod(clazz: PsiElement): PsiElement? =
        when (checkTestClass(clazz)) {
            UNSURE -> super.findSetUpMethod(clazz)
            NO -> null
            else -> clazz.asKtClassOrObject()?.let(psiBasedDelegate::findSetUp)
        }

    override fun findTearDownMethod(clazz: PsiElement): PsiElement? =
        when (checkTestClass(clazz)) {
            UNSURE -> super.findTearDownMethod(clazz)
            NO -> null
            else -> clazz.asKtClassOrObject()?.let(psiBasedDelegate::findTearDown)
        }

    override fun findBeforeSuiteMethod(clazz: PsiClass): PsiElement? {
        return when (checkTestClass(clazz)) {
            UNSURE -> super.findBeforeSuiteMethod(clazz)
            NO -> null
            else -> clazz.asKtClassOrObject()?.let(psiBasedDelegate::findBeforeSuite)
        }
    }

    override fun findAfterSuiteMethod(clazz: PsiClass): PsiElement? {
        return when (checkTestClass(clazz)) {
            UNSURE -> super.findAfterSuiteMethod(clazz)
            NO -> null
            else -> clazz.asKtClassOrObject()?.let(psiBasedDelegate::findAfterSuite)
        }
    }

    override fun isIgnoredMethod(element: PsiElement?): Boolean =
        when (checkTestClass(element)) {
            UNSURE -> super.isIgnoredMethod(element)
            NO -> false
            else -> element.asKtNamedFunction()?.let(psiBasedDelegate::isIgnoredMethod) ?: false
        }

    override fun isTestMethod(element: PsiElement?): Boolean =
        when (checkTestClass(element)) {
            UNSURE -> super.isTestMethod(element)
            NO -> false
            else -> element.asKtNamedFunction()?.let(psiBasedDelegate::isTestMethod) ?: false
        }

    override fun getLanguage(): Language = KotlinLanguage.INSTANCE

    override fun isTestMethod(declaration: KtNamedFunction): Boolean =
        psiBasedDelegate.isTestMethod(declaration)

    override fun isIgnoredMethod(declaration: KtNamedFunction): Boolean =
        psiBasedDelegate.isIgnoredMethod(declaration)

    override fun getSetUpMethodFileTemplateDescriptor(): FileTemplateDescriptor? {
        return if (KotlinPluginModeProvider.isK1Mode()) {
            super.getSetUpMethodFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin JUnit5 SetUp Function.kt")
        }
    }

    override fun getTearDownMethodFileTemplateDescriptor(): FileTemplateDescriptor? {
        return if (KotlinPluginModeProvider.isK1Mode()) {
            super.getTearDownMethodFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin JUnit5 TearDown Function.kt")
        }
    }

    override fun getTestMethodFileTemplateDescriptor(): FileTemplateDescriptor {
        return if (KotlinPluginModeProvider.isK1Mode()) {
            super.getTestMethodFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin JUnit5 Test Function.kt")
        }
    }

    override fun getTestClassFileTemplateDescriptor(): FileTemplateDescriptor? =
        if (KotlinPluginModeProvider.isK1Mode()) {
            super.getTestClassFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin JUnit5 Test Class.kt")
        }
}

private val METHOD_ANNOTATION_FQN = setOf(
    JUnitUtil.TEST5_ANNOTATION,
    KotlinPsiBasedTestFramework.KOTLIN_TEST_TEST,
    ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST,
    ORG_JUNIT_JUPITER_API_REPEATED_TEST,
    ORG_JUNIT_JUPITER_API_TEST_FACTORY,
    "org.junit.jupiter.api.TestTemplate",
    "org.junitpioneer.jupiter.RetryingTest"
)

private val setUpAnnotations = setOf(JUnitUtil.BEFORE_EACH_ANNOTATION_NAME, KotlinPsiBasedTestFramework.KOTLIN_TEST_BEFORE_TEST)
private val tearDownAnnotations = setOf(JUnitUtil.AFTER_EACH_ANNOTATION_NAME, KotlinPsiBasedTestFramework.KOTLIN_TEST_AFTER_TEST)
private val testableAnnotations = METHOD_ANNOTATION_FQN + setUpAnnotations + tearDownAnnotations

