// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin

import org.jetbrains.kotlin.jsr223.Jsr223KotlincProvider
import org.jetbrains.kotlin.jsr223.KotlinJsr223StandardScriptEngineFactory4IdeaBase

@Suppress("IO_FILE_USAGE")
class KotlinJsr223StandardScriptEngineFactory4Idea : KotlinJsr223StandardScriptEngineFactory4IdeaBase({ Jsr223KotlincProvider.ideKotlinc.toFile() })
