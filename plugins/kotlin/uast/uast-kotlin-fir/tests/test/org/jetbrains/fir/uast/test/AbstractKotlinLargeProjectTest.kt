// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.fir.uast.test

import com.intellij.platform.uast.testFramework.env.AbstractLargeProjectTest
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts

abstract class AbstractKotlinLargeProjectTest : AbstractLargeProjectTest() {
    override val projectLibraries
        get() = listOf(Pair("KotlinStdlibTestArtifacts", listOf(TestKotlinArtifacts.kotlinStdlib.toFile())))
}