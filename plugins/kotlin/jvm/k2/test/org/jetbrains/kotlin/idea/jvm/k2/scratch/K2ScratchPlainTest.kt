// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.runner.RunWith

@TestRoot("jvm/k2")
@TestDataPath($$"$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners::class)
@TestMetadata("testData/scratch")
class K2ScratchPlainTest : AbstractK2ScratchRunActionTest() {
    override val isExplainEnabled: Boolean
        get() = false

    @TestMetadata("destructuringDecls.kts")
    fun testDestructuringDecls() = doScratchTest("testData/scratch/destructuringDecls.kts")

    @TestMetadata("for.kts")
    fun testFor() = doScratchTest("testData/scratch/for.kts")

    @TestMetadata("generalCollections.kts")
    fun testGeneralCollections() = doScratchTest("testData/scratch/generalCollections.kts")

    @TestMetadata("generics.kts")
    fun testGenerics() = doScratchTest("testData/scratch/generics.kts")

    @TestMetadata("hexFormat.kts")
    fun testHexFormat() = doScratchTest("testData/scratch/hexFormat.kts")

    @TestMetadata("jdk17HexFormat.kts")
    fun testJdk17HexFormat() = doScratchTest("testData/scratch/jdk17HexFormat.kts")

    @TestMetadata("klass.kts")
    fun testKlass() = doScratchTest("testData/scratch/klass.kts")

    @TestMetadata("unresolved.kts")
    fun testUnresolved() = doScratchTest("testData/scratch/unresolved.kts")

    @TestMetadata("var.kts")
    fun testVar() = doScratchTest("testData/scratch/var.kts")

    @TestMetadata("veryLongOutput.kts")
    fun testVeryLongOutput() = doScratchTest("testData/scratch/veryLongOutput.kts")

    @TestMetadata("when.kts")
    fun testWhen() = doScratchTest("testData/scratch/when.kts")
}
