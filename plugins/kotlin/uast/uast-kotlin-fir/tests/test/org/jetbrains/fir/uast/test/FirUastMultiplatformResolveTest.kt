// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.fir.uast.test

import com.intellij.platform.uast.testFramework.env.findUElementByTextFromPsi
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.VfsTestUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.test.KotlinLightMultiplatformCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinMultiPlatformProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinMultiPlatformProjectDescriptor.PlatformDescriptor
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.toUElementOfType
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class FirUastMultiplatformResolveTest : KotlinLightMultiplatformCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor(): KotlinMultiPlatformProjectDescriptor = KotlinMultiPlatformProjectDescriptor.COMMON_ONLY

    fun testExpectClassSuperTypeCall() {
        doTest(
            $$"""
                class ParsingException(position: Int, message: String) : Exception("Invalid input at $position: $message")
                """.trimIndent(),
            $$"""
                Exception("Invalid input at $position: $message")
                """.trimIndent(),
        )
    }

    fun testExpectClassConstructorCall() {
        doTest(
            """fun fail(): Nothing = throw IllegalStateException("Something went wrong")""",
            """IllegalStateException("Something went wrong")""",
        )
    }

    fun testGenericExpectClassConstructorCall() {
        doTest(
            "fun cache(): HashMap<String, Int> = HashMap()",
            "HashMap()",
        )
    }

    fun testTopLevelFunctionWithoutJvmFacade() {
        doTest(
            "fun scope() = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)",
            "CoroutineScope(kotlinx.coroutines.Dispatchers.Default)",
        )
    }

    fun testReifiedTypeOfCall() {
        doTest(
            """
                import kotlin.reflect.typeOf

                inline fun <reified T> kType() = typeOf<T>()
                """.trimIndent(),
            "typeOf<T>()",
        )
    }

    fun testImplicitDelegationCallOfSecondaryConstructor() {
        configureCommonFile("class Sample { constructor() }")

        val delegationCall = PsiTreeUtil.findChildOfType(myFixture.file, KtConstructorDelegationCall::class.java)
        assertNotNull("No constructor delegation call", delegationCall)
        val call = delegationCall.toUElementOfType<UCallExpression>()
        assertNotNull("The constructor delegation call is not converted to UAST", call)
        checkResolvedMethod(call!!, "the implicit constructor delegation call")
    }

    private fun doTest(@Language("kotlin") fileText: String, @Language("kotlin") callText: String) {
        configureCommonFile(fileText)
        val call = myFixture.file.findUElementByTextFromPsi<UCallExpression>(callText)
        checkResolvedMethod(call, "'$callText'")
    }

    private fun configureCommonFile(@Language("kotlin") fileText: String) {
        val sourceRoot = PlatformDescriptor.COMMON.sourceRoot() ?: error("No source root for ${PlatformDescriptor.COMMON}")
        val virtualFile = VfsTestUtil.createFile(sourceRoot, "main.kt", fileText)
        myFixture.configureFromExistingVirtualFile(virtualFile)
    }

    private fun checkResolvedMethod(call: UCallExpression, callDescription: String) {
        val method = call.resolve()
        assertNotNull("$callDescription is not resolved", method)
        method!!

        resolveClassReference(method.returnType)
        for (parameter in method.parameterList.parameters) {
            resolveClassReference(parameter.type)
        }
        assertNotNull("${method::class.simpleName} for $callDescription has no containing file", method.containingFile)
    }

    private fun resolveClassReference(type: PsiType?) {
        (type as? PsiClassType)?.resolve()
    }
}
