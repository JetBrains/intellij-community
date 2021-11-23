// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin

import org.junit.Test

class KotlinUastValuesTest : AbstractKotlinValuesTest() {
    override fun getTestDataPath(): String = TEST_KOTLIN_MODEL_DIR.absolutePath

    @Test
    fun testAssertion() = doTest("Assertion")

    @Test
    fun testDelegate() = doTest("Delegate")

    @Test
    fun testIn() = doTest("In")

    @Test
    fun testLocalDeclarations() = doTest("LocalDeclarations")

    @Test
    fun testSimple() = doTest("Simple")

    @Test
    fun testStringTemplateComplex() = doTest("StringTemplateComplex")
}