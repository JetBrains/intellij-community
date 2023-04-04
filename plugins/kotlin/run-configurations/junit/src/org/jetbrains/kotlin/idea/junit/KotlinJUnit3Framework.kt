// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit

import com.intellij.execution.junit.JUnit3Framework
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
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class KotlinJUnit3Framework: JUnit3Framework(), KotlinPsiBasedTestFramework {
    private val psiBasedDelegate = object : AbstractKotlinPsiBasedTestFramework() {
        override val markerClassFqn: String
            get() = JUnitUtil.TEST_CASE_CLASS

        override val disabledTestAnnotation: String
            get() = "org.junit.Ignore"

        override fun isTestClass(declaration: KtClassOrObject): Boolean {
            return super.isTestClass(declaration) && CachedValuesManager.getCachedValue(declaration) {
                CachedValueProvider.Result.create(isJUnit3TestClass(declaration, mutableSetOf()), OuterModelsModificationTrackerManager.getInstance(declaration.project).tracker)
            }
        }

        override fun isTestMethod(declaration: KtNamedFunction): Boolean {
            if (!super.isTestMethod(declaration)) return false
            if (declaration.name?.startsWith("test") != true) return false
            val classOrObject = declaration.getParentOfType<KtClassOrObject>(true) ?: return false
            if (classOrObject is KtClass && classOrObject.isInner()) return false

            return isTestClass(classOrObject)
        }

        override fun findSetUp(classOrObject: KtClassOrObject): KtNamedFunction? =
            findFunctionWithName(classOrObject.takeIf { isTestClass(it) }, "setUp")

        override fun findTearDown(classOrObject: KtClassOrObject): KtNamedFunction? =
            findFunctionWithName(classOrObject.takeIf { isTestClass(it) }, "tearDown")

        private fun findFunctionWithName(classOrObject: KtClassOrObject?, name: String): KtNamedFunction? {
            if (classOrObject == null) return null
            for (declaration in classOrObject.declarations) {
                if (declaration is KtNamedFunction && declaration.name == name) {
                    return declaration
                }
            }
            return null
        }

        private fun isJUnit3TestClass(declaration: KtClassOrObject, visitedShortNames: MutableSet<String>): Boolean {
            if (!isFrameworkAvailable(declaration)) return false
            if (declaration is KtClass && declaration.isInner()) return false
            for (superTypeEntry in declaration.superTypeListEntries) {
                if (superTypeEntry is KtSuperTypeCallEntry && superTypeEntry.valueArguments.isEmpty()) {
                    val containingFile = declaration.containingKtFile
                    val superShortName = superTypeEntry.calleeExpression.text

                    // circular dependency detected
                    if (!visitedShortNames.add(superShortName)) return false

                    if (checkNameMatch(containingFile, CLASS_ANNOTATION_FQN, superShortName)) {
                        return true
                    }

                    containingFile.declarations.firstOrNull { it is KtClassOrObject && it.name == superShortName }?.let {
                        return isJUnit3TestClass(it as KtClassOrObject, visitedShortNames)
                    }
                }
            }
            return false
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

    private companion object {
        private val CLASS_ANNOTATION_FQN = setOf(JUnitUtil.TEST_CASE_CLASS)
    }
}
