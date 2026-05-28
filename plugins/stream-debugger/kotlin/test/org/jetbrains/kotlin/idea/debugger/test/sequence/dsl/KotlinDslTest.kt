// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.test.sequence.dsl

import com.intellij.debugger.streams.core.trace.dsl.impl.DslImpl
import com.intellij.debugger.streams.test.DslTestCase
import com.intellij.openapi.application.PluginPathManager
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.debugger.sequence.trace.dsl.KotlinCollectionsPeekCallFactory
import org.jetbrains.kotlin.idea.debugger.sequence.trace.dsl.KotlinStatementFactory
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinDslTest : DslTestCase(DslImpl(KotlinStatementFactory(KotlinCollectionsPeekCallFactory()))), ExpectedPluginModeProvider {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K1
    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    override fun getTestDataPath(): String {
        return PluginPathManager.getPluginHomePath("stream-debugger") + "/testData/sequence/dsl"
    }
}