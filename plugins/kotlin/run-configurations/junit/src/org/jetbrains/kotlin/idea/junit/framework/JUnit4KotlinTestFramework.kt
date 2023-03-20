// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit.framework

import com.intellij.execution.junit.JUnitUtil
import com.intellij.lang.OuterModelsModificationTrackerManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.kotlin.idea.testIntegration.framework.AbstractKotlinTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFramework.Companion.KOTLIN_TEST_AFTER_TEST
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFramework.Companion.KOTLIN_TEST_BEFORE_TEST
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFramework.Companion.KOTLIN_TEST_TEST
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class JUnit4KotlinTestFramework : AbstractKotlinTestFramework() {
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

    companion object {
        private val testMethodAnnotations = setOf(JUnitCommonClassNames.ORG_JUNIT_TEST, KOTLIN_TEST_TEST)
        private val setUpAnnotations = setOf(JUnitUtil.BEFORE_ANNOTATION_NAME, KOTLIN_TEST_BEFORE_TEST)
        private val tearDownAnnotations = setOf(JUnitUtil.AFTER_ANNOTATION_NAME, KOTLIN_TEST_AFTER_TEST)
    }
}