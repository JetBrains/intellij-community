// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

abstract class AbstractK1IdeK2CodeKotlinSteppingPacketsNumberTest : AbstractK1KotlinSteppingPacketsNumberTest() {
    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
    override val compileWithK2 = true
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1
}