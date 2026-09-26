// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import org.jetbrains.uast.test.kotlin.TEST_KOTLIN_MODEL_PATH
import org.junit.Test
import java.nio.file.Path

class KotlinUastIdentifiersTest : AbstractFirUastIdentifiersTest() {

    override val testBasePath: Path = TEST_KOTLIN_MODEL_PATH

    @Test
    fun testLocalDeclarations() = doCheck("LocalDeclarations.kt")

    @Test
    fun testComments() = doCheck("Comments.kt")

    @Test
    fun testConstructors() = doCheck("Constructors.kt")

    @Test
    fun testSimpleAnnotated() = doCheck("SimpleAnnotated.kt")

    @Test
    fun testAnonymous() = doCheck("Anonymous.kt")

    @Test
    fun testLambdas() = doCheck("Lambdas.kt")

    @Test
    fun testSuperCalls() = doCheck("SuperCalls.kt")

    @Test
    fun testPropertyInitializer() = doCheck("PropertyInitializer.kt")

    @Test
    fun testEnumValuesConstructors() = doCheck("EnumValuesConstructors.kt")

    @Test
    fun testNonTrivialIdentifiers() = doCheck("NonTrivialIdentifiers.kt")

    @Test
    fun testBrokenDataClass() = doCheck("BrokenDataClass.kt")

    @Test
    fun testBrokenGeneric() = doCheck("BrokenGeneric.kt")
}