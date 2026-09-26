// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import org.jetbrains.uast.test.kotlin.TEST_KOTLIN_MODEL_PATH
import org.junit.Test
import java.nio.file.Path

class KotlinUastValuesTest : AbstractFirUastValuesTest() {

    override val testBasePath: Path = TEST_KOTLIN_MODEL_PATH

    @Test
    fun testAssertion() = doCheck("Assertion.kt")

    @Test
    fun testDelegate() = doCheck("Delegate.kt")

    @Test
    fun testIn() = doCheck("In.kt")

    @Test
    fun testLocalDeclarations() = doCheck("LocalDeclarations.kt")

    @Test
    fun testSimple() = doCheck("Simple.kt")

    @Test
    fun testStringTemplateComplex() = doCheck("StringTemplateComplex.kt")
}