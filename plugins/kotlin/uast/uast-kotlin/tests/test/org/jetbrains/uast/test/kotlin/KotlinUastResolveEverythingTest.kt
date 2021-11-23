// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.kotlin

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.kotlin.common.ResolveEverythingTestBase
import org.junit.Test


class KotlinUastResolveEverythingTest : AbstractKotlinUastTest(), ResolveEverythingTestBase {

    override fun check(testName: String, file: UFile) {
        super.check(testName, file)
    }

    @Test
    fun testClassAnnotation() = doTest("ClassAnnotation")

    @Test
    fun testLocalDeclarations() = doTest("LocalDeclarations")

    @Test
    fun testConstructors() = doTest("Constructors")

    @Test
    fun testSimpleAnnotated() = doTest("SimpleAnnotated")

    @Test
    fun testAnonymous() = doTest("Anonymous")

    @Test
    fun testTypeReferences() = doTest("TypeReferences")

    @Test
    fun testImports() = doTest("Imports")

    @Test
    fun testReifiedResolve() = doTest("ReifiedResolve")

    @Test
    fun testResolve() = doTest("Resolve")

    @Test
    fun testPropertyReferences() = doTest("PropertyReferences")

    @Test
    fun testTypeAliasConstructorReference() = doTest("TypeAliasConstructorReference")
}