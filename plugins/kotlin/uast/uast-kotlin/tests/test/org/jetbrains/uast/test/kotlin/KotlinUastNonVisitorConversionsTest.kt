// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.kotlin.org.jetbrains.uast.test.kotlin

import org.junit.Test

class KotlinUastNonVisitorConversionsTest : AbstractKotlinNonVisitorConversionsTest() {

    @Test
    fun testClassAnnotation() = doTest("ClassAnnotation")

    @Test
    fun testLocalDeclarations() = doTest("LocalDeclarations")

    @Test
    fun testComments() = doTest("Comments")

    @Test
    fun testConstructors() = doTest("Constructors")

    @Test
    fun testSimpleAnnotated() = doTest("SimpleAnnotated")

    @Test
    fun testAnonymous() = doTest("Anonymous")
    
    @Test
    fun testAnnotationParameters() = doTest("AnnotationParameters")

    @Test
    fun testLambdas() = doTest("Lambdas")

    @Test
    fun testSuperCalls() = doTest("SuperCalls")

    @Test
    fun testPropertyInitializer() = doTest("PropertyInitializer")

    @Test
    fun testEnumValuesConstructors() = doTest("EnumValuesConstructors")

    @Test
    fun testNonTrivialIdentifiers() = doTest("NonTrivialIdentifiers")

    @Test
    fun testBrokenDataClass() = doTest("BrokenDataClass")

    @Test
    fun testBrokenGeneric() = doTest("BrokenGeneric")

    @Test
    fun testTryCatch() = doTest("TryCatch")

}