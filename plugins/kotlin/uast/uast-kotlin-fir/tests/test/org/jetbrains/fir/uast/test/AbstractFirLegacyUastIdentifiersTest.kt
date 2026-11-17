// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.LegacyUastIdentifiersTestBase
import kotlin.io.path.absolute

abstract class AbstractFirLegacyUastIdentifiersTest : AbstractFirUastIdentifiersTest(), LegacyUastIdentifiersTestBase {
    private val whitelist: Set<String> by lazy {
        setOf(
            // TODO: Also failed with FE1.0 UAST
            "DestructuringDeclaration.kt",
            "LambdaReturn.kt",
            "WhenAndDestructing.kt",

            // TODO: this file fails 'testIdentifiersParents' check
            "DataClassInheritsAbstractClassWithEquals.kt",
        ).mapTo(mutableSetOf()) { testDataDirectory.toPath().resolve(it).absolute().normalize().toString() }
    }

    override fun isExpectedToFail(filePath: String): Boolean {
        return filePath in whitelist
    }

    override fun check(filePath: String, file: UFile) {
        super<LegacyUastIdentifiersTestBase>.check(filePath, file)
    }
}
