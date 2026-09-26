// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import org.junit.platform.commons.util.AnnotationUtils
import java.util.stream.Stream

/**
 * # Simple junit5 based test method intended for writing unit tests
 *
 * ## When to use
 * This test fixture can be used for
 * - testing small utility functions
 * - testing simple PSI-based code
 * - testing small analysis-api based code
 *
 * for anything larger, please use test-data-based tests and generate K1 & K2 variants.
 *
 * ## K1 vs. K2
 * This test fixture is intended to write quick unit tests and therefore does not
 * require splitting the tests into `AbstractXyzTest`, `K1XyzTest` and `K2XyzTest`.
 * However, the tests still require to be executed in the context of the Kotlin Plugin.
 * K1: `kotlin.all-tests`
 * K2: `kotlin.fir-all-tests`
 *
 * The test will detect in which context it runs.
 * If the test shall only exist in either K1 or K2, providing the desired modes is possible
 * ```kotlin
 * class MyTest {
 *     @KotlinPluginUnitTest(KotlinPluginMode.K1) // <- only contributes the test in K1 environments
 *     fun onlyK1() {
 *
 *     }
 *
 *     @KotlinPluginUnitTest(KotlinPluginMode.K2) // <- only contributes the test in K2 environments
 *     fun onlyK2() {
 *
 *     }
 * }
 * ```
 *
 * ## Running from IDEA
 * Since this test is expected to run in the runtime classpath of either K1 or K2, it is expected
 * to select a proper classpath (cp) for run configurations.
 *
 * - K1: `kotlin.all-tests`
 * - K2: `kotlin.fir-all-tests`
 *
 * Tip: When executing such tests often, it might be helpful to select the desired cp in the Junit run configuration template.
 *
 * ## Samples
 * ### Test with source code
 * ```kotlin
 * class MyTest {
 *     @KotlinPluginUnitTest
 *     fun testWithSourceCode(project: Project) {
 *         val ktClass = KtPsiFactory(project).createClass("class A")
 *         // ...
 *     }
 * }
 * ```
 *
 * ### Test with known [KotlinPluginMode]
 * ```kotlin
 * class MyTest {
 *     @KotlinPluginUnitTest
 *     fun test(mode: KotlinPluginMode) {
 *         // ...
 *     }
 * }
 * ```
 *
 * ### Test with [JavaCodeInsightTestFixture]
 * ```kotlin
 * class MyTest {
 *     @KotlinPluginUnitTest
 *     fun test(fixture: JavaCodeInsightTestFixture) {
 *
 *     }
 * }
 * ```
 */

@TestTemplate
@ExtendWith(KotlinPluginUnitTestExtension::class)
annotation class KotlinPluginUnitTest

internal class KotlinPluginUnitTestExtension : TestTemplateInvocationContextProvider {
    override fun supportsTestTemplate(context: ExtensionContext): Boolean {
        if (context.testMethod.isEmpty) return false
        val testMethod = context.testMethod.get()
        return AnnotationUtils.isAnnotated(testMethod, KotlinPluginUnitTest::class.java)
    }

    override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
        return Stream.of(KotlinPluginUnitTestInvocationContext())
    }
}

private class KotlinPluginUnitTestInvocationContext(
) : TestTemplateInvocationContext {
    override fun getDisplayName(invocationIndex: Int): String = "Project resolver"

    override fun getAdditionalExtensions(): List<Extension?> =
        listOf(
            LightJavaCodeInsightTestFixtureExtension5()
        )
}


private class LightJavaCodeInsightTestFixtureExtension5 : BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private val namespace = ExtensionContext.Namespace.create(LightJavaCodeInsightFixtureTestCase5::class.java)

    @Suppress("JUnitMalformedDeclaration") // 'private class' is OK as this is just a wrapper and not intended to be executed
    private class LightJavaCodeInsightFixtureTestCaseWrapper : LightJavaCodeInsightFixtureTestCase() {
        val fixture get() = myFixture

        fun beforeEach() {
            setUp()
        }

        fun afterEach() {
            tearDown()
        }
    }

    private fun ExtensionContext.getTestCaseWrapper(): LightJavaCodeInsightFixtureTestCaseWrapper {
        return getStore(namespace).getOrComputeIfAbsent(LightJavaCodeInsightFixtureTestCaseWrapper::class.java.name) {
            LightJavaCodeInsightFixtureTestCaseWrapper()
        } as LightJavaCodeInsightFixtureTestCaseWrapper
    }

    override fun beforeEach(context: ExtensionContext) {
        context.getTestCaseWrapper().beforeEach()
    }

    override fun afterEach(context: ExtensionContext) {
        context.getTestCaseWrapper().afterEach()
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Boolean {
        return parameterContext.parameter.type == JavaCodeInsightTestFixture::class.java ||
                parameterContext.parameter.type == CodeInsightTestFixture::class.java ||
                parameterContext.parameter.type == Project::class.java
    }

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Any? {
        if (parameterContext.parameter.type == JavaCodeInsightTestFixture::class.java ||
            parameterContext.parameter.type == CodeInsightTestFixture::class.java
        ) {
            val testCaseWrapper = extensionContext.getTestCaseWrapper()
            return testCaseWrapper.fixture
        }

        if (parameterContext.parameter.type == Project::class.java) {
            return extensionContext.getTestCaseWrapper().fixture.project
        }

        return null
    }
}
