/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.fir.uast

import org.jetbrains.kotlin.idea.fir.uast.env.kotlin.AbstractFirUastTest
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastResolveEverythingTestBase

abstract class AbstractFirUastResolveEverythingTest : AbstractFirUastTest(), UastResolveEverythingTestBase {
    override val isFirUastPlugin: Boolean = true

    private val failingTests: Set<String> = setOf(
        // caused by KT-51491
        "/uast-kotlin/tests/testData/BrokenGeneric.kt",
    )

    override fun isExpectedToFail(filePath: String, fileContent: String): Boolean {
        return failingTests.any { filePath.endsWith(it) } || super.isExpectedToFail(filePath, fileContent)
    }

    override fun check(filePath: String, file: UFile) {
        super.check(filePath, file)
    }

    fun doTest(filePath: String) {
        doCheck(filePath)
    }
}
