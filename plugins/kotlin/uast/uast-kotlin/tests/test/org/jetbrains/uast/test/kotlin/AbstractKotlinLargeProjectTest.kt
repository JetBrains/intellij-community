// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin

import com.intellij.platform.uast.testFramework.env.AbstractLargeProjectTest
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts

abstract class AbstractKotlinLargeProjectTest : AbstractLargeProjectTest() {
    override val projectLibraries
        get() = listOf(Pair("KotlinStdlibTestArtifacts", listOf(TestKotlinArtifacts.kotlinStdlib.toFile())))
}