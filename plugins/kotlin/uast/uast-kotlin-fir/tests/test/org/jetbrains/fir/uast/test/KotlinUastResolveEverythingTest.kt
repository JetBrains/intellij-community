// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import org.jetbrains.fir.uast.test.env.kotlin.AbstractFirUastTest
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.resolvableWithTargets
import org.jetbrains.uast.test.env.kotlin.assertEqualsToFile
import org.jetbrains.uast.test.kotlin.TEST_KOTLIN_MODEL_PATH
import org.junit.Test
import java.nio.file.Path

class KotlinUastResolveEverythingTest : AbstractFirUastTest() {
    override val testBasePath: Path = TEST_KOTLIN_MODEL_PATH

    override fun check(filePath: String, file: UFile) {
        val expected = Path.of(filePath.removeSuffix(".kt") + ".resolved.txt").toFile()
        assertEqualsToFile("resolved", expected, file.resolvableWithTargets())
    }

    @Test
    fun testClassAnnotation() = doCheck("ClassAnnotation.kt")

    @Test
    fun testLocalDeclarations() = doCheck("LocalDeclarations.kt")

    @Test
    fun testConstructors() = doCheck("Constructors.kt")

    @Test
    fun testSimpleAnnotated() = doCheck("SimpleAnnotated.kt")

    @Test
    fun testAnonymous() = doCheck("Anonymous.kt")

    @Test
    fun testTypeReferences() = doCheck("TypeReferences.kt")

    @Test
    fun testImports() = doCheck("Imports.kt")

    @Test
    fun testReifiedResolve() = doCheck("ReifiedResolve.kt")

    @Test
    fun testResolve() = doCheck("Resolve.kt")

    @Test
    fun testPropertyReferences() = doCheck("PropertyReferences.kt")

    @Test
    fun testTypeAliasConstructorReference() = doCheck("TypeAliasConstructorReference.kt")

    @Test
    fun testComments() = doCheck("Comments.kt")
}