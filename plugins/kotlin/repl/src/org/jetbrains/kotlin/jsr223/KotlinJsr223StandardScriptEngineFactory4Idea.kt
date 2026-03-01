// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jsr223

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout

@K1Deprecation
class KotlinJsr223StandardScriptEngineFactory4Idea : KotlinJsr223StandardScriptEngineFactory4IdeaBase(KotlinPluginLayout::kotlinc)