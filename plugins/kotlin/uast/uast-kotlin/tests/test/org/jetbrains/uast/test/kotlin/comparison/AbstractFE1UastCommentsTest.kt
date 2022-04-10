// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.kotlin.comparison

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastCommentLogTestBase
import org.jetbrains.uast.test.kotlin.AbstractKotlinUastTest
import java.io.File

abstract class AbstractFE1UastCommentsTest : AbstractKotlinUastTest(), UastCommentLogTestBase {
    override val isFirUastPlugin: Boolean = false

    override fun check(filePath: String, file: UFile) {
        super<UastCommentLogTestBase>.check(filePath, file)
    }

    override var testDataDir = File("plugins/uast-kotlin-fir/testData")

    fun doTest(filePath: String) {
        testDataDir = File(filePath).parentFile
        val testName = filePath.substring(filePath.lastIndexOf('/') + 1).removeSuffix(".kt")
        val virtualFile = getVirtualFile(testName)

        val psiFile = psiManager.findFile(virtualFile) ?: error("Can't get psi file for $testName")
        val uFile = uastContext.convertElementWithParent(psiFile, null) ?: error("Can't get UFile for $testName")
        check(filePath, uFile as UFile)
    }
}
