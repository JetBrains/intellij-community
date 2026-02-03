// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test.codeinsight

import com.intellij.debugger.codeinsight.ControlExceptionBreakpointJVMSupport
import com.intellij.xdebugger.codeinsight.ControlExceptionBreakpointSupport
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.util.elementByOffset

/**
 * Tests for Kotlin support in [ControlExceptionBreakpointJVMSupport].
 */
class ControlExceptionBreakpointKotlinSupportTest : KotlinLightCodeInsightFixtureTestCase() {

    private fun findExceptionReference(text: String): ControlExceptionBreakpointSupport.ExceptionReference? {
        assertTrue(text.contains("<caret>"))
        myFixture.configureByText("A.kt", text)
        myFixture.doHighlighting()
        val support = ControlExceptionBreakpointJVMSupport()
        val exRef = support.findExceptionReference(project, myFixture.elementByOffset)
        return exRef
    }

    private fun checkAvailable(text: String, exceptionName: String) {
        val exRef = findExceptionReference(text)
        assertNotNull(exRef)
        assertEquals(exceptionName, exRef!!.displayName)
    }

    private fun checkUnavailable(text: String) {
        val exRef = findExceptionReference(text)
        assertNull(exRef)
    }

    private fun methodBody(body: String) =
        "fun f() { $body }"

    fun testNew() = checkAvailable(
        methodBody("val o = Runtime<caret>Exception()"),
        "RuntimeException"
    )

    fun testType() = checkAvailable(
        methodBody("val o: <caret>Throwable = RuntimeException()"),
        "Throwable"
    )

    fun testTypeUnavailable() = checkUnavailable(
        methodBody("val o: <caret>Any = RuntimeException()")
    )

    fun testCatch() = checkAvailable(
        methodBody("try { } catch (e: Runtime<caret>Exception) { }"),
        "RuntimeException"
    )

    fun testThrow() = checkAvailable(
        methodBody("try { } catch (e: RuntimeException) { <caret>throw e }"),
        "RuntimeException"
    )

    fun testClass() = checkAvailable(
        "class <caret>A : RuntimeException() {}",
        "A"
    )

    fun testClassUnavailable() = checkUnavailable(
        "class <caret>A {}"
    )
}