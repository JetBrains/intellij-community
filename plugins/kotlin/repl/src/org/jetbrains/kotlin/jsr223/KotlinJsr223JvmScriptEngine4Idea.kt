// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jsr223

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import java.io.File
import javax.script.ScriptContext
import javax.script.ScriptEngineFactory
import kotlin.reflect.KClass

// keep for backward compatibility with 3-rd party plugins.
@K1Deprecation
@Suppress("unused")
class KotlinJsr223JvmScriptEngine4Idea(
    factory: ScriptEngineFactory,
    templateClasspath: List<File>,
    templateClassName: String,
    getScriptArgs: (ScriptContext, Array<out KClass<out Any>>?) -> ScriptArgsWithTypes?,
    scriptArgsTypes: Array<out KClass<out Any>>?
) : KotlinJsr223JvmScriptEngine4IdeaBase(
    factory, templateClasspath, templateClassName, KotlinPluginLayout::kotlinc, getScriptArgs, scriptArgsTypes
)