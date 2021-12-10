// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.uast

import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.test.KtAssert
import org.jetbrains.uast.test.common.kotlin.UastResolveApiFixtureTestBase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class FirUastResolveApiFixtureTest : KotlinLightCodeInsightFixtureTestCase(), UastResolveApiFixtureTestBase {
    override val isFirUastPlugin: Boolean = true

    private val whitelist : Set<String> = setOf(
        // TODO: Expected: PROPERTY, Actual: LightVariableBuilder:s
        "ResolveStringFromUast",
        // TODO: multiResolve, getArgumentForParameter
        "MultiResolve",
        // TODO: multiResolve, getArgumentForParameter
        "MultiResolveJava",
        // TODO: multiResolve, getArgumentForParameter, return type for ambiguous call
        "MultiResolveJavaAmbiguous",
        // TODO: resolve to setter, not getter
        "ResolveFromBaseJava",
        // TODO: multiResolve
        "MultiResolveInClass",
        // TODO: multiResolve, return type for ambiguous call
        "MultiConstructorResolve",
        // TODO: multiResolve
        "MultiInvokableObjectResolve",
        // TODO: multiResolve
        "MultiResolveJvmOverloads",
        // TODO: local resolution
        "LocalResolve",
        // TODO: resolve annotation param to annotation ctor ??
        "ResolveCompiledAnnotation",
        // TODO: need further investigation
        "AssigningArrayElementType",
    )

    private fun isExpectedToFail(key: String): Boolean {
        return key in whitelist
    }

    private fun doCheckRunner(key: String, checkCallback: () -> Unit) {
        try {
            checkCallback()
            if (isExpectedToFail(key)) {
                KtAssert.fail("This test seems not fail anymore. Drop this from the white-list and re-run the test.")
            }
        } catch (e: AssertionError) {
            if (!isExpectedToFail(key)) throw e
        }
    }

    private fun doCheck(key: String, checkCallback: (JavaCodeInsightTestFixture, Project) -> Unit) {
        doCheckRunner(key) {
            checkCallback(myFixture, project)
        }
    }

    private fun doCheck(key: String, checkCallback: (JavaCodeInsightTestFixture) -> Unit) {
        doCheckRunner(key) {
            checkCallback(myFixture)
        }
    }

    fun testResolveStringFromUast() {
        doCheck("ResolveStringFromUast", ::checkResolveStringFromUast)
    }

    fun testMultiResolve() {
        doCheck("MultiResolve", ::checkMultiResolve)
    }

    fun testMultiResolveJava() {
        doCheck("MultiResolveJava", ::checkMultiResolveJava)
    }

    fun testMultiResolveJavaAmbiguous() {
        doCheck("MultiResolveJavaAmbiguous", ::checkMultiResolveJavaAmbiguous)
    }

    fun testResolveFromBaseJava() {
        doCheck("ResolveFromBaseJava", ::checkResolveFromBaseJava)
    }

    fun testMultiResolveInClass() {
        doCheck("MultiResolveInClass", ::checkMultiResolveInClass)
    }

    fun testMultiConstructorResolve() {
        doCheck("MultiConstructorResolve", ::checkMultiConstructorResolve)
    }

    fun testMultiInvokableObjectResolve() {
        doCheck("MultiInvokableObjectResolve", ::checkMultiInvokableObjectResolve)
    }

    fun testMultiResolveJvmOverloads() {
        doCheck("MultiResolveJvmOverloads", ::checkMultiResolveJvmOverloads)
    }

    fun testLocalResolve() {
        doCheck("LocalResolve", ::checkLocalResolve)
    }

    fun testResolveCompiledAnnotation() {
        doCheck("ResolveCompiledAnnotation", ::checkResolveCompiledAnnotation)
    }

    fun testAssigningArrayElementType() {
        doCheck("AssigningArrayElementType", ::checkAssigningArrayElementType)
    }

    fun testDivByZero() {
        doCheck("DivByZero", ::checkDivByZero)
    }
}
