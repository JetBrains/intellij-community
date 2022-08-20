// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.kotlin.env

import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.test.kotlin.AbstractKotlinUastTest
import java.nio.file.Paths

abstract class AbstractFE1UastTest : AbstractKotlinUastTest() {
    override var testDataDir = KotlinRoot.PATH.resolve("uast/uast-kotlin/tests/testData").toFile()

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
