/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.uast.env.kotlin

import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.test.kotlin.AbstractKotlinUastTest
import java.io.File
import java.nio.file.Paths

abstract class AbstractFE1UastTest : AbstractKotlinUastTest() {
    override var testDataDir = File("testData")

    fun doTest(filePath: String) {
        val normalizedFile = Paths.get(filePath).normalize().toFile()
        testDataDir = normalizedFile.parentFile
        val testName = normalizedFile.nameWithoutExtension
        val virtualFile = getVirtualFile(testName)

        val psiFile = psiManager.findFile(virtualFile) ?: error("Can't get psi file for $testName")
        val uFile = UastFacade.convertElementWithParent(psiFile, null) ?: error("Can't get UFile for $testName")
        check(normalizedFile.toString(), uFile as UFile)
    }
}
