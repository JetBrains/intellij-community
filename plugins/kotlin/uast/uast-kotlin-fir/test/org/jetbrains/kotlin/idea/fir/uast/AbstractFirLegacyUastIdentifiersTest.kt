// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.uast

import org.jetbrains.kotlin.test.KotlinRoot
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.LegacyUastIdentifiersTestBase
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute

abstract class AbstractFirLegacyUastIdentifiersTest : AbstractFirUastIdentifiersTest(), LegacyUastIdentifiersTestBase {
    @OptIn(ExperimentalPathApi::class)
    private val whitelist : Set<String> = setOf(
        // TODO: Also failed with FE1.0 UAST
        "uast-kotlin/testData/DestructuringDeclaration.kt",
        "uast-kotlin/testData/LambdaReturn.kt",
        "uast-kotlin/testData/WhenAndDestructing.kt",
        // TODO: incorrect parent chain for annotations?
        "uast-kotlin/testData/ParameterPropertyWithAnnotation.kt",
        "uast-kotlin/testData/PropertyWithAnnotation.kt",
        "uast-kotlin/testData/SimpleAnnotated.kt",
        "uast-kotlin/testData/ReifiedParameters.kt",
        "uast-kotlin/testData/ReceiverFun.kt",
        // TODO: incorrect parent chain for setter parameter
        "uast-kotlin/testData/PropertyAccessors.kt",
        "uast-kotlin/testData/PropertyInitializer.kt",
    ).mapTo(mutableSetOf()) { KotlinRoot.DIR_PATH.resolve("uast").resolve(it).absolute().normalize().toString() }

    override fun isExpectedToFail(filePath: String): Boolean {
        return filePath in whitelist
    }

    override fun check(filePath: String, file: UFile) {
        super<LegacyUastIdentifiersTestBase>.check(filePath, file)
    }
}
