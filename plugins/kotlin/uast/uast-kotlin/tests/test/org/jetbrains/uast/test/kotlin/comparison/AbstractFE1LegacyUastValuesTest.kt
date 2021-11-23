// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.kotlin.comparison

import org.jetbrains.uast.test.common.kotlin.LegacyUastValuesTestBase
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute

abstract class AbstractFE1LegacyUastValuesTest : AbstractFE1UastValuesTest(), LegacyUastValuesTestBase {
    // TODO: better not to see exceptions from legacy UAST
    @OptIn(ExperimentalPathApi::class)
    private val whitelist: Set<String> = listOf(
        // TODO: div-by-zero error!
        "uast-kotlin/tests/testData/Bitwise.kt",
    ).mapTo(mutableSetOf()) { Paths.get("..").resolve(it).absolute().normalize().toString() }

    override fun isExpectedToFail(filePath: String): Boolean {
        return filePath in whitelist
    }
}
