// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypes
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.jetbrains.kotlin.idea.k2.refactoring.inline.J2KInlineCache.Companion.findUsageReplacementStrategy
import org.jetbrains.kotlin.idea.k2.refactoring.inline.J2KInlineCache.Companion.setUsageReplacementStrategy
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtReferenceExpression

class InlineCacheTest : LightPlatformCodeInsightTestCase() {
    fun `test valid value`() {
        val method = createJavaMethod()
        method.setUsageReplacementStrategy(strategy)
        assertNotNull(method.findUsageReplacementStrategy(withValidation = true))
        assertNotNull(method.findUsageReplacementStrategy(withValidation = false))
    }

    fun `test valid value after change`() {
        val method = createJavaMethod()
        method.setUsageReplacementStrategy(strategy)
        val originalText = method.text
        val body = method.body
        val javaExpression = createJavaDeclaration()
        val newElement = body?.addAfter(javaExpression, body.statements.first())
        assertTrue(originalText != method.text)
        newElement?.delete()
        assertTrue(originalText == method.text)

        assertNotNull(method.findUsageReplacementStrategy(withValidation = true))
        assertNotNull(method.findUsageReplacementStrategy(withValidation = false))
    }

    fun `test invalid value`() {
        val method = createJavaMethod()
        method.setUsageReplacementStrategy(strategy)
        val originalText = method.text
        method.body?.statements?.firstOrNull()?.delete()

        assertTrue(originalText != method.text)
        assertNull(method.findUsageReplacementStrategy(withValidation = true))
        assertNotNull(method.findUsageReplacementStrategy(withValidation = false))
    }

    private fun createJavaMethod(): PsiMethod = javaFactory.createMethodFromText(
        """void dummyFunction() {
                |    int a = 4;
                |}
            """.trimMargin(),
        null,
    )

    private fun createJavaDeclaration(): PsiDeclarationStatement = javaFactory.createVariableDeclarationStatement(
      "number",
      PsiTypes.intType(),
      javaFactory.createExpressionFromText("1 + 3", null),
    )

    private val javaFactory: PsiElementFactory get() = JavaPsiFacade.getElementFactory(project)

    private val strategy = object : UsageReplacementStrategy {
        override fun createReplacer(usage: KtReferenceExpression): () -> KtElement? = TODO()
    }
}