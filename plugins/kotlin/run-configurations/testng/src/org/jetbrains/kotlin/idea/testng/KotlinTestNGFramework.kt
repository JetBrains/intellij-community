// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testng

import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.java.analysis.OuterModelsModificationTrackerManager
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentOfType
import com.intellij.util.ThreeState
import com.intellij.util.ThreeState.*
import com.theoryinpractice.testng.TestNGFramework
import com.theoryinpractice.testng.util.TestNGUtil
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

class KotlinTestNGFramework: TestNGFramework(), KotlinPsiBasedTestFramework {
    private val psiBasedDelegate = object : AbstractKotlinPsiBasedTestFramework() {
        override val markerClassFqn: String = TestNGUtil.TEST_ANNOTATION_FQN
        override val disabledTestAnnotation: String
            get() = throw UnsupportedOperationException("TestNG does not support Ignore methods")
        override val allowTestMethodsInObject: Boolean = false

        override fun checkTestClass(declaration: KtClassOrObject): ThreeState {
            val checkState = super.checkTestClass(declaration)
            if (checkState != UNSURE) return checkState

            return CachedValuesManager.getCachedValue(declaration) {
                CachedValueProvider.Result.create(checkTestNGTestClass(declaration), OuterModelsModificationTrackerManager.getTracker(declaration.project))
            }
        }

        fun isPotentialTestClass(element: PsiElement): Boolean {
            if (element.language != KotlinLanguage.INSTANCE) return false
            val psiElement = (element as? KtLightElement<*, *>)?.kotlinOrigin ?: element
            val ktClassOrObject = psiElement.parentOfType<KtClassOrObject>(true) ?: return false

            return CachedValuesManager.getCachedValue(ktClassOrObject) {
                CachedValueProvider.Result.create(
                    checkTestNGPotentialTestClass(ktClassOrObject) != NO,
                    OuterModelsModificationTrackerManager.getTracker(ktClassOrObject.project)
                )
            }
        }

        override fun isTestMethod(declaration: KtNamedFunction): Boolean {
            return when {
                !super.isTestMethod(declaration) -> false
                declaration.annotationEntries.isEmpty() -> false
                else -> isTestNGTestMethod(declaration)
            }
        }

        override fun findSetUp(classOrObject: KtClassOrObject): KtNamedFunction? =
            findAnnotatedFunction(classOrObject.takeIf { isTestClass(it) }, setUpAnnotations)

        override fun findTearDown(classOrObject: KtClassOrObject): KtNamedFunction? =
            findAnnotatedFunction(classOrObject.takeIf { isTestClass(it) }, tearDownAnnotations)

        private fun isTestNGTestMethod(declaration: KtNamedFunction): Boolean {
            val classOrObject = declaration.getParentOfType<KtClassOrObject>(true) ?: return false
            return isTestClass(classOrObject) && isAnnotated(declaration, testAnnotations)
        }

        private fun checkTestNGTestClass(declaration: KtClassOrObject): ThreeState =
            if (!isFrameworkAvailable(declaration)) {
                NO
            } else {
                checkIsTestNGLikeTestClass(declaration, false)
            }

        private fun checkTestNGPotentialTestClass(declaration: KtClassOrObject): ThreeState =
            if (!isFrameworkAvailable(declaration)) {
                NO
            } else {
                checkIsTestNGLikeTestClass(declaration, true)
            }

        private fun checkIsTestNGLikeTestClass(declaration: KtClassOrObject, isPotential: Boolean): ThreeState {
            return if (isPotential && isUnderTestSources(declaration)) {
                UNSURE
            } else if (declaration.isPrivate()) {
                NO
            } else if (declaration.safeAs<KtClass>()?.isInner() == true) {
                NO
            } else if (declaration.isTopLevel() && isAnnotated(declaration, TestNGUtil.TEST_ANNOTATION_FQN)) {
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
                        if (referencedName == declaration.name && findAnnotatedFunction(subClass, testAnnotations) != null) {
                            return YES
                        }
                    }
                }
                UNSURE
            } else {
                NO
            }
        }

        override fun isIgnoredMethod(declaration: KtNamedFunction): Boolean {
            return findAnnotation(declaration, testAnnotations)?.let {
                it.valueArguments.find { argument -> argument.getArgumentName()?.asName?.asString() == "enabled" }?.getArgumentExpression()?.text == "false"
            } == true
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
            FileTemplateDescriptor("Kotlin TestNG SetUp Function.kt")
        }
    }

    override fun getTearDownMethodFileTemplateDescriptor(): FileTemplateDescriptor? {
        return if (KotlinPluginModeProvider.isK1Mode()) {
            super.getTearDownMethodFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin TestNG TearDown Function.kt")
        }
    }

    override fun getTestMethodFileTemplateDescriptor(): FileTemplateDescriptor {
        return if (KotlinPluginModeProvider.isK1Mode()) {
            super.getTestMethodFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin TestNG Test Function.kt")
        }
    }

    override fun getTestClassFileTemplateDescriptor(): FileTemplateDescriptor? =
        if (KotlinPluginModeProvider.isK1Mode()) {
            super.getTestClassFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin TestNG Test Class.kt")
        }

    override fun getParametersMethodFileTemplateDescriptor(): FileTemplateDescriptor? {
        return if (KotlinPluginModeProvider.isK1Mode()) {
            super.getParametersMethodFileTemplateDescriptor()
        } else {
            FileTemplateDescriptor("Kotlin TestNG Parameters Function.kt")
        }
    }
}

private val testAnnotations = setOf(TestNGUtil.TEST_ANNOTATION_FQN)
private val setUpAnnotations = setOf("org.testng.annotations.BeforeMethod", "org.testng.annotations.BeforeClass")
private val tearDownAnnotations = setOf("org.testng.annotations.AfterMethod", "org.testng.annotations.AfterClass")
private val testableClassMethodAnnotations = testAnnotations + setUpAnnotations + tearDownAnnotations
