// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.jsr223

import org.jetbrains.kotlin.config.KotlinCompilerVersion
import kotlin.script.experimental.jvm.jsr223.base.KotlinJsr223ScriptEngineFactoryBase

/**
 * Supplies the Kotlin compiler version to the relocated, version-agnostic
 * [KotlinJsr223ScriptEngineFactoryBase]: reporting it requires the compiler on the classpath, which
 * that base class (in the `kotlin-scripting-jvm` artifact) does not have.
 */
abstract class KotlinJsr223JvmScriptEngineFactoryBase : KotlinJsr223ScriptEngineFactoryBase() {

    override fun getLanguageVersion(): String = KotlinCompilerVersion.VERSION
    override fun getEngineVersion(): String = KotlinCompilerVersion.VERSION
}
