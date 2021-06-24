/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.uast

import org.jetbrains.kotlin.idea.fir.uast.common.kotlin.FirLegacyUastValuesTestBase
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute

abstract class AbstractFE1LegacyUastValuesTest : AbstractFE1UastValuesTest(), FirLegacyUastValuesTestBase {
    // TODO: better not to see exceptions from legacy UAST
    @OptIn(ExperimentalPathApi::class)
    private val whitelist: Set<String> = listOf(
        // TODO: div-by-zero error!
        "uast-kotlin/testData/Bitwise.kt",
    ).mapTo(mutableSetOf()) { Paths.get("..").resolve(it).absolute().normalize().toString() }

    override fun isExpectedToFail(filePath: String): Boolean {
        return filePath in whitelist
    }
}
