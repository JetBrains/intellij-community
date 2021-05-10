// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.build

import org.jetbrains.kotlin.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.util.slashedPath

class IncrementalConstantSearchTest : AbstractIncrementalJpsTest() {
    fun testJavaConstantChangedUsedInKotlin() {
        doTest("jps/jps-plugin/tests/testData/incremental/custom/javaConstantChangedUsedInKotlin")
    }

    fun testJavaConstantUnchangedUsedInKotlin() {
        doTest("jps/jps-plugin/tests/testData/incremental/custom/javaConstantUnchangedUsedInKotlin")
    }

    fun testKotlinConstantChangedUsedInJava() {
        doTest("jps/jps-plugin/tests/testData/incremental/custom/kotlinConstantChangedUsedInJava")
    }

    fun testKotlinJvmFieldChangedUsedInJava() {
        doTest("jps/jps-plugin/tests/testData/incremental/custom/kotlinJvmFieldChangedUsedInJava")
    }

    fun testKotlinConstantUnchangedUsedInJava() {
        doTest("jps/jps-plugin/tests/testData/incremental/custom/kotlinConstantUnchangedUsedInJava")
    }

    fun testKotlinJvmFieldUnchangedUsedInJava() {
        doTest("jps/jps-plugin/tests/testData/incremental/custom/kotlinJvmFieldUnchangedUsedInJava")
    }

    override fun doTest(testDataPath: String) {
        super.doTest(KotlinRoot.DIR.resolve(testDataPath).slashedPath)
    }
}