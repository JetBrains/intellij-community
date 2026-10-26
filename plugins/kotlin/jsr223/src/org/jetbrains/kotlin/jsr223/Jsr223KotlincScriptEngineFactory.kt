// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.jsr223

import org.jetbrains.kotlin.ide.kotlinc.AnalyzerKotlincProvider

@Suppress("IO_FILE_USAGE")
class Jsr223KotlincScriptEngineFactory :
    KotlinJsr223StandardScriptEngineFactory4IdeaBase({ AnalyzerKotlincProvider.getKotlincHomeBlocking().toFile() }) {
    override fun getNames(): List<String> = super.getNames() + KOTLIN_IDE_JSR223_SCRIPT_ENGINE_NAME
}

private const val KOTLIN_IDE_JSR223_SCRIPT_ENGINE_NAME = "kotlin-ide-jsr223"