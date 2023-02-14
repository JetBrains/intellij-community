// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.kotlin

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.kotlin.org.jetbrains.uast.test.kotlin.common.TypesTestBaseDuplicate
import org.junit.Test

class KotlinUastTypesTest : AbstractKotlinTypesTest(), TypesTestBaseDuplicate {

    override fun check(testName: String, file: UFile) {
        super<TypesTestBaseDuplicate>.check(testName, file)
    }

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
