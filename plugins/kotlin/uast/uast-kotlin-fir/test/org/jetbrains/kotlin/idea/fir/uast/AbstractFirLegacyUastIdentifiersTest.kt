// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.uast

import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.LegacyUastIdentifiersTestBase
import kotlin.io.path.absolute

abstract class AbstractFirLegacyUastIdentifiersTest : AbstractFirUastIdentifiersTest(), LegacyUastIdentifiersTestBase {
    private val whitelist : Set<String> = setOf(
        // TODO: Also failed with FE1.0 UAST
        "uast-kotlin/tests/testData/DestructuringDeclaration.kt",
        "uast-kotlin/tests/testData/LambdaReturn.kt",
        "uast-kotlin/tests/testData/WhenAndDestructing.kt",

        // TODO: this file fails 'testIdentifiersParents' check
        "uast-kotlin/tests/testData/DataClassInheritsAbstractClassWithEquals.kt",
    ).mapTo(mutableSetOf()) { KotlinRoot.PATH.resolve("uast").resolve(it).absolute().normalize().toString() }

    override fun isExpectedToFail(filePath: String): Boolean {
        return filePath in whitelist
    }

    override fun check(filePath: String, file: UFile) {
        super<LegacyUastIdentifiersTestBase>.check(filePath, file)
    }
}
