// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.fir.uast.test

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.uast.UElement
import org.jetbrains.uast.test.kotlin.KotlinIDERenderLogTest
import java.io.File

internal class FirKotlinIDERenderLogTest: KotlinIDERenderLogTest() {
    override fun checkLeak(node: UElement) {}

    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    override fun getRenderFile(testName: String): File {
        val firFile = getTestFile(testName, "render.fir.txt")
        if (firFile.exists()) return firFile
        return super.getRenderFile(testName)
    }

    override fun getLogFile(testName: String): File {
        val logFile = getTestFile(testName, "log.fir.txt")
        if (logFile.exists()) return logFile
        return super.getLogFile(testName)
    }
}

