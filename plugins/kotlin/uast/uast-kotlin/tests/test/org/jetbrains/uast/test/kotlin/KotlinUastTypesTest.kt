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
