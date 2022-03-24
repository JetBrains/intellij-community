// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jsr223

import org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineFactoryBase
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import kotlin.script.experimental.jvm.util.KotlinJars
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContextOrStdlib
import javax.script.ScriptContext
import javax.script.ScriptEngine

@Suppress("unused") // used in javax.script.ScriptEngineFactory META-INF file
class KotlinJsr223StandardScriptEngineFactory4Idea : KotlinJsr223JvmScriptEngineFactoryBase() {

    override fun getScriptEngine(): ScriptEngine =
        KotlinJsr223JvmScriptEngine4Idea(
            this,
            scriptCompilationClasspathFromContextOrStdlib(wholeClasspath = true) + KotlinJars.kotlinScriptStandardJars,
            "kotlin.script.templates.standard.ScriptTemplateWithBindings",
            { ctx, argTypes -> ScriptArgsWithTypes(arrayOf(ctx.getBindings(ScriptContext.ENGINE_SCOPE)), argTypes ?: emptyArray()) },
            arrayOf(Map::class)
        )
}

