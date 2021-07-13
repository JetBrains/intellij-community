// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin

import org.junit.Test

class KotlinUastTypesTest : AbstractKotlinTypesTest() {
    @Test fun testLocalDeclarations() = doTest("LocalDeclarations")

    @Test fun testUnexpectedContainerException() = doTest("UnexpectedContainerException")

    @Test fun testCycleInTypeParameters() = doTest("CycleInTypeParameters")

    @Test fun testEa101715() = doTest("ea101715")

    @Test fun testStringTemplate() = doTest("StringTemplate")

    @Test fun testStringTemplateComplex() = doTest("StringTemplateComplex")

    @Test fun testInferenceInsideUnresolvedConstructor() = doTest("InferenceInsideUnresolvedConstructor")

    @Test fun testInnerNonFixedTypeVariable() = doTest("InnerNonFixedTypeVariable")
    
    @Test fun testAnnotatedTypes() = doTest("AnnotatedTypes")
}


@Target(AnnotationTarget.TYPE)
annotation class MyAnnotation(val a: Int, val b: String, val c: AnnotationTarget)

fun foo(list: List<@MyAnnotation(1, "str", AnnotationTarget.TYPE) String>) {
    val a = list[2]
    val b: @MyAnnotation(2, "boo", AnnotationTarget.FILE) String = "abc"
    val c = b
}