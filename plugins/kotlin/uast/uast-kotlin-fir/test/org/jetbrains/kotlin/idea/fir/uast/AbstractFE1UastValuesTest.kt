/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.uast

import org.jetbrains.uast.UFile
import org.jetbrains.kotlin.idea.fir.uast.common.kotlin.FirUastValuesTestBase
import org.jetbrains.kotlin.idea.fir.uast.env.kotlin.AbstractFE1UastTest

abstract class AbstractFE1UastValuesTest : AbstractFE1UastTest(), FirUastValuesTestBase {
    override val isFirUastPlugin: Boolean = false

    override fun check(filePath: String, file: UFile) {
        super<FirUastValuesTestBase>.check(filePath, file)
    }
}
