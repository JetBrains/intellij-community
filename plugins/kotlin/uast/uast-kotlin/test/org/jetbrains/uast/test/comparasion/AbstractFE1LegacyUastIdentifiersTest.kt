// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.comparasion

import org.jetbrains.uast.test.common.kotlin.LegacyUastIdentifiersTestBase

abstract class AbstractFE1LegacyUastIdentifiersTest : AbstractFE1UastIdentifiersTest(), LegacyUastIdentifiersTestBase {
    // TODO: better not to see exceptions from legacy UAST
    private val whitelist : Set<String> = setOf(
        "plugins/uast-kotlin/testData/DestructuringDeclaration.kt",
        "plugins/uast-kotlin/testData/LambdaReturn.kt",
        "plugins/uast-kotlin/testData/WhenAndDestructing.kt"
    )
    override fun isExpectedToFail(filePath: String): Boolean {
        return filePath in whitelist
    }
}
