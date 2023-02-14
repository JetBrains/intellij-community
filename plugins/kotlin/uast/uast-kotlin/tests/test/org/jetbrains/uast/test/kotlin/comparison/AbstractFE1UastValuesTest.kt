// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.kotlin.comparison

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastValuesTestBase
import org.jetbrains.uast.test.kotlin.env.AbstractFE1UastTest

abstract class AbstractFE1UastValuesTest : AbstractFE1UastTest(), UastValuesTestBase {
    override val isFirUastPlugin: Boolean = false

    override fun check(filePath: String, file: UFile) {
        super.check(filePath, file)
    }
}
