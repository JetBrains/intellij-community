// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import org.jetbrains.uast.test.kotlin.TEST_KOTLIN_MODEL_PATH
import org.junit.Test
import java.nio.file.Path

class KotlinUastTypesTest : AbstractFirUastTypesTest() {

    override val testBasePath: Path = TEST_KOTLIN_MODEL_PATH

    @Test
    fun testLocalDeclarations() = doCheck("LocalDeclarations.kt")

    @Test
    fun testUnexpectedContainerException() = doCheck("UnexpectedContainerException.kt")

    @Test
    fun testCycleInTypeParameters() = doCheck("CycleInTypeParameters.kt")

    @Test
    fun testEa101715() = doCheck("ea101715.kt")

    @Test
    fun testStringTemplate() = doCheck("StringTemplate.kt")

    @Test
    fun testStringTemplateComplex() = doCheck("StringTemplateComplex.kt")

    @Test
    fun testInferenceInsideUnresolvedConstructor() = doCheck("InferenceInsideUnresolvedConstructor.kt")

    @Test
    fun testInnerNonFixedTypeVariable() = doCheck("InnerNonFixedTypeVariable.kt")

    @Test
    fun testAnnotatedTypes() = doCheck("AnnotatedTypes.kt")
}
