/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.uast.test.kotlin.comparison

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastResolveEverythingTestBase
import org.jetbrains.uast.test.kotlin.env.AbstractFE1UastTest

abstract class AbstractFE1UastResolveEverythingTest : AbstractFE1UastTest(), UastResolveEverythingTestBase {
    override val isFirUastPlugin: Boolean = false

    override fun check(filePath: String, file: UFile) {
        super<UastResolveEverythingTestBase>.check(filePath, file)
    }
}
