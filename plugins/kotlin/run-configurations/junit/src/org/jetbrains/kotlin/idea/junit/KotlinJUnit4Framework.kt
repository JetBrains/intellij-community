// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit

import com.intellij.execution.junit.JUnit4Framework
import com.intellij.execution.junit.JUnitUtil
import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.java.analysis.OuterModelsModificationTrackerManager
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentOfType
import com.intellij.util.ThreeState
import com.intellij.util.ThreeState.*
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.testIntegration.framework.AbstractKotlinPsiBasedTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtClassOrObject
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtNamedFunction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinJUnit4Framework: JUnit4Framework(), KotlinPsiBasedTestFramework {
    private val psiBasedDelegate = object : AbstractKotlinPsiBasedTestFramework() {
        override val markerClassFqn: String = JUnitUtil.TEST_ANNOTATION
        override val disabledTestAnnotation: String = "org.junit.Ignore"
        override val allowTestMethodsInObject: Boolean = false

        override fun checkTestClass(declaration: KtClassOrObject): ThreeState {
            val checkState = super.checkTestClass(declaration)
            if (checkState != UNSURE) return checkState

            return CachedValuesManager.getCachedValue(declaration) {
                CachedValueProvider.Result.create(checkJUnit4TestClass(declaration), OuterModelsModificationTrackerManager.getTracker(declaration.project))
            }
        }

        public override fun isFrameworkAvailable(element: KtElement): Boolean {
            return super.isFrameworkAvailable(element) || isFrameworkAvailable(element, KotlinPsiBasedTestFramework.KOTLIN_TEST_TEST, false)
        }

        fun isPotentialTestClass(element: PsiElement): Boolean {
            if (element.language != KotlinLanguage.INSTANCE) return false
            val psiElement = (element as? KtLightElement<*, *>)?.kotlinOrigin ?: element
            val ktClassOrObject = psiElement.parentOfType<KtClassOrObject>(true) ?: return false

            return CachedValuesManager.getCachedValue(ktClassOrObject) {
                CachedValueProvider.Result.create(
                    checkJUnit4PotentialTestClass(ktClassOrObject) != NO,
                    OuterModelsModificationTrackerManager.getTracker(ktClassOrObject.project)
                )
            }
        }

        override fun isTestMethod(declaration: KtNamedFunction): Boolean {
            return when {
                !super.isTestMethod(declaration) -> false
                declaration.annotationEntries.isEmpty() -> false
                else -> isJUnit4TestMethod(declaration)
            }
        }

        override fun findSetUp(classOrObject: KtClassOrObject): KtNamedFunction? =
            findAnnotatedFunction(classOrObject.takeIf { isTestClass(it) }, setUpAnnotations)

        override fun findTearDown(classOrObject: KtClassOrObject): KtNamedFunction? =
            findAnnotatedFunction(classOrObject.takeIf { isTestClass(it) }, tearDownAnnotations)

        private fun isJUnit4TestMethod(declaration: KtNamedFunction): Boolean {
            val classOrObject = declaration.getParentOfType<KtClassOrObject>(true) ?: return false
            return isTestClass(classOrObject) && isAnnotated(declaration, testMethodAnnotations)
        }

        private fun checkJUnit4TestClass(declaration: KtClassOrObject): ThreeState =
            if (!isFrameworkAvailable(declaration)) {
                NO
            } else {
                checkIsJUnit4LikeTestClass(declaration, false)
            }

        private fun checkJUnit4PotentialTestClass(declaration: KtClassOrObject): ThreeState =
            if (!isFrameworkAvailable(declaration)) {
                NO
            } else {
                checkIsJUnit4LikeTestClass(declaration, true)
            }

        private fun checkIsJUnit4LikeTestClass(declaration: KtClassOrObject, isPotential: Boolean): ThreeState {
            return if (isPotential && isUnderTestSources(declaration)) {
                UNSURE
            } else if (declaration.isPrivate()) {
                NO
            } else if (declaration.safeAs<KtClass>()?.isInner() == true) {
                NO
            } else if (declaration.isTopLevel() && isAnnotated(declaration, JUnitUtil.RUN_WITH)) {
                YES
            } else if (findAnnotatedFunction(declaration, testableClassMethodAnnotations) != null) {
                YES
            } else if (declaration.hasModifier(KtTokens.OPEN_KEYWORD) || declaration.hasModifier(KtTokens.ABSTRACT_KEYWORD)) {
                for (subDeclaration in declaration.declarations) {
                    val subClass = subDeclaration as? KtClassOrObject ?: continue
                    val superTypeListEntries = subDeclaration.superTypeListEntries
                    for (superTypeListEntry in superTypeListEntries) {
                        val referencedName =
                            (superTypeListEntry as? KtSuperTypeCallEntry)?.calleeExpression?.constructorReferenceExpression?.getReferencedName()
                        if (referencedName == declaration.name && findAnnotatedFunction(subClass, testableClassMethodAnnotations) != null) {
                            return YES
                        }
                    }
                }
                UNSURE
            } else {
                NO
            }
        }
    }

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
            FileTemplateDescriptor("Kotlin JUnit4 SetUp Function.kt")
        }
    }

    override fun getTearDownMethodFileTemplateDescriptor(): FileTemplateDescriptor? {
        return if (KotlinPluginModeProvider.isK1Mode()) {
            super.getTearDownMethodFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin JUnit4 TearDown Function.kt")
        }
    }

    override fun getTestMethodFileTemplateDescriptor(): FileTemplateDescriptor {
        return if (KotlinPluginModeProvider.isK1Mode()) {
            super.getTestMethodFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin JUnit4 Test Function.kt")
        }
    }

    override fun getParametersMethodFileTemplateDescriptor(): FileTemplateDescriptor? {
        return if (KotlinPluginModeProvider.isK1Mode()) {
            super.getParametersMethodFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin JUnit4 Parameters Function.kt")
        }
    }

    override fun getTestClassFileTemplateDescriptor(): FileTemplateDescriptor? =
        if (KotlinPluginModeProvider.isK1Mode()) {
            super.getTestClassFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin JUnit4 Test Class.kt")
        }

    override fun isFrameworkAvailable(clazz: PsiElement): Boolean {
        return super.isFrameworkAvailable(clazz) || clazz is KtClass && psiBasedDelegate.isFrameworkAvailable(clazz)
    }
}

private val testMethodAnnotations = setOf(JUnitCommonClassNames.ORG_JUNIT_TEST, KotlinPsiBasedTestFramework.KOTLIN_TEST_TEST)
private val setUpAnnotations = setOf(JUnitUtil.BEFORE_ANNOTATION_NAME, KotlinPsiBasedTestFramework.KOTLIN_TEST_BEFORE_TEST)
private val tearDownAnnotations = setOf(JUnitUtil.AFTER_ANNOTATION_NAME, KotlinPsiBasedTestFramework.KOTLIN_TEST_AFTER_TEST)
private val testableClassMethodAnnotations = testMethodAnnotations + setUpAnnotations + tearDownAnnotations
