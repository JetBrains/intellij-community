// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit

import com.intellij.execution.junit.JUnit4Framework
import com.intellij.execution.junit.JUnitUtil
import com.intellij.lang.Language
import com.intellij.lang.OuterModelsModificationTrackerManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.testIntegration.framework.AbstractKotlinPsiBasedTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtClassOrObject
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtNamedFunction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinJUnit4Framework: JUnit4Framework(), KotlinPsiBasedTestFramework {
    private val psiBasedDelegate = object : AbstractKotlinPsiBasedTestFramework() {
        override val markerClassFqn: String = JUnitUtil.TEST_ANNOTATION
        override val disabledTestAnnotation: String = "org.junit.Ignore"

        override fun isTestClass(declaration: KtClassOrObject): Boolean {
            return super.isTestClass(declaration) && CachedValuesManager.getCachedValue(declaration) {
                CachedValueProvider.Result.create(isJUnit4TestClass(declaration), OuterModelsModificationTrackerManager.getInstance(declaration.project).tracker)
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

        private fun isJUnit4TestClass(declaration: KtClassOrObject): Boolean {
            return if (!isFrameworkAvailable(declaration)) {
                false
            } else if (declaration.safeAs<KtClass>()?.isInner() == true) {
                false
            } else if (declaration.isTopLevel() && isAnnotated(declaration, JUnitUtil.RUN_WITH)) {
                true
            } else if (findAnnotatedFunction(declaration, testMethodAnnotations) != null) {
                true
            } else if (declaration.hasModifier(KtTokens.OPEN_KEYWORD) || declaration.hasModifier(KtTokens.ABSTRACT_KEYWORD)) {
                for (subDeclaration in declaration.declarations) {
                    val subClass = subDeclaration as? KtClassOrObject ?: continue
                    val superTypeListEntries = subDeclaration.superTypeListEntries
                    for (superTypeListEntry in superTypeListEntries) {
                        val referencedName =
                            (superTypeListEntry as? KtSuperTypeCallEntry)?.calleeExpression?.constructorReferenceExpression?.getReferencedName()
                        if (referencedName == declaration.name && findAnnotatedFunction(subClass, testMethodAnnotations) != null) {
                            return true
                        }
                    }
                }
                false
            } else {
                false
            }
        }

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
        private val testMethodAnnotations = setOf(JUnitCommonClassNames.ORG_JUNIT_TEST, KotlinPsiBasedTestFramework.KOTLIN_TEST_TEST)
        private val setUpAnnotations = setOf(JUnitUtil.BEFORE_ANNOTATION_NAME, KotlinPsiBasedTestFramework.KOTLIN_TEST_BEFORE_TEST)
        private val tearDownAnnotations = setOf(JUnitUtil.AFTER_ANNOTATION_NAME, KotlinPsiBasedTestFramework.KOTLIN_TEST_AFTER_TEST)
    }
}
