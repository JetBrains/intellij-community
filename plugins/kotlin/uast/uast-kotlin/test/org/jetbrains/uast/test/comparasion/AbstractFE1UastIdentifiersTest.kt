// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.comparasion

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastIdentifiersTestBase
import org.jetbrains.uast.test.kotlin.org.jetbrains.uast.test.env.kotlin.AbstractFE1UastTest

abstract class AbstractFE1UastIdentifiersTest : AbstractFE1UastTest(), UastIdentifiersTestBase {
    override val isFirUastPlugin: Boolean = false

    override fun check(filePath: String, file: UFile) {
        super<UastIdentifiersTestBase>.check(filePath, file)
    }
}
