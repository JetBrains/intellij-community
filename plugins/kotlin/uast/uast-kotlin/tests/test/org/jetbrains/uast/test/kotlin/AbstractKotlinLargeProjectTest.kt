// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin

import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.uast.test.env.AbstractLargeProjectTest

abstract class AbstractKotlinLargeProjectTest : AbstractLargeProjectTest() {
    override val projectLibraries
        get() = listOf(Pair("KotlinStdlibTestArtifacts", listOf(KotlinArtifacts.instance.kotlinStdlib)))
}