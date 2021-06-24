// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.uast

import org.jetbrains.uast.UFile
import org.jetbrains.kotlin.idea.fir.uast.common.kotlin.FirUastIdentifiersTestBase
import org.jetbrains.kotlin.idea.fir.uast.env.kotlin.AbstractFirUastTest

abstract class AbstractFirUastIdentifiersTest : AbstractFirUastTest(), FirUastIdentifiersTestBase {
    override val isFirUastPlugin: Boolean = true

    override fun check(filePath: String, file: UFile) {
        super.check(filePath, file)
    }

    fun doTest(filePath: String) {
        doCheck(filePath)
    }
}
