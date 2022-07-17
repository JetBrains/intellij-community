// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.jsr223

import org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineFactoryBase
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import javax.script.ScriptContext
import javax.script.ScriptEngine
import kotlin.script.experimental.jvm.util.KotlinJars
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContextOrStdlib

class KotlinJsr223StandardScriptEngineFactory4Idea : KotlinJsr223JvmScriptEngineFactoryBase() {

    override fun getEngineName(): String {
        return "Kotlin - Beta"
    }

    override fun getScriptEngine(): ScriptEngine =
        KotlinJsr223JvmScriptEngine4Idea(
            this,
            scriptCompilationClasspathFromContextOrStdlib(wholeClasspath = true) + KotlinJars.kotlinScriptStandardJars,
            "kotlin.script.templates.standard.ScriptTemplateWithBindings",
            { ctx, argTypes -> ScriptArgsWithTypes(arrayOf(ctx.getBindings(ScriptContext.ENGINE_SCOPE)), argTypes ?: emptyArray()) },
            arrayOf(Map::class)
        )
}

