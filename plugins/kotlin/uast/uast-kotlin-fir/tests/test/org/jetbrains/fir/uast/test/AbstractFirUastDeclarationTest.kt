// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import org.jetbrains.fir.uast.test.env.kotlin.AbstractFirUastTest
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastRenderLogTestBase

abstract class AbstractFirUastDeclarationTest : AbstractFirUastTest(), UastRenderLogTestBase {
    override val isFirUastPlugin: Boolean = true

    override fun check(filePath: String, file: UFile) {
        super.check(filePath, file)
    }

    fun doTest(filePath: String) {
        doCheck(filePath)
    }
}
