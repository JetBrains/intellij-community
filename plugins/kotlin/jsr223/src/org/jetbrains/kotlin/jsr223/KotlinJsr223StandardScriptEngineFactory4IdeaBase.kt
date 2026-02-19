// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.jsr223

import org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineFactoryBase
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import java.io.File
import javax.script.ScriptContext
import javax.script.ScriptEngine
import kotlin.script.experimental.jvm.util.KotlinJars
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContextOrStdlib

abstract class KotlinJsr223StandardScriptEngineFactory4IdeaBase(
    private val kotlincDirProvider: () -> File,
) : KotlinJsr223JvmScriptEngineFactoryBase() {

    override fun getEngineName(): String {
        return "Kotlin - Beta"
    }

    override fun getScriptEngine(): ScriptEngine =
        KotlinJsr223JvmScriptEngine4IdeaBase(
            this,
            scriptCompilationClasspathFromContextOrStdlib(wholeClasspath = true) + KotlinJars.kotlinScriptStandardJars,
            "kotlin.script.templates.standard.ScriptTemplateWithBindings",
            kotlincDirProvider,
            { ctx, argTypes -> ScriptArgsWithTypes(arrayOf(ctx.getBindings(ScriptContext.ENGINE_SCOPE)), argTypes ?: emptyArray()) },
            arrayOf(Map::class)
        )
}

