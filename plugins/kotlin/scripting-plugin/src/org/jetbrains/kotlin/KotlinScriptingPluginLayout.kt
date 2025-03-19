// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin

import com.intellij.ide.plugins.getPluginDistDirByClass
import java.io.File
import kotlin.io.path.exists

internal object KotlinScriptingPluginLayout {
    val kotlinc: File by lazy {
        val pluginRoot = getPluginDistDirByClass(KotlinScriptingPluginLayout::class.java)
            ?: error("Can't find jar file for ${KotlinScriptingPluginLayout::class.simpleName}")

        val kotlinc = pluginRoot.resolve("kotlinc")
        if (!kotlinc.exists()) {
            error("kotlinc is not found for kotlin-scripting plugin")
        }

        kotlinc.toFile()
    }
}