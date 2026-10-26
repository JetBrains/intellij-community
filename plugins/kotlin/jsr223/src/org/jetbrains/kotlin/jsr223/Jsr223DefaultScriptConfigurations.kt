// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.jsr223

import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.prependSyntheticSnippets
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.refineConfigurationBeforeEvaluate
import kotlin.script.experimental.jvm.jsr223.configureExposedJsr223Context
import kotlin.script.experimental.jvm.jsr223.generateBindingSnippetIfNeeded
import kotlin.script.experimental.jvm.jsr223.importAllBindings
import kotlin.script.experimental.jvm.jsr223.jsr223

/**
 * A copy of `KotlinJsr223DefaultScriptCompilationConfiguration`, because `kotlin-scripting-jsr223` is not published for the IDE.
 *
 * The configuration does not reach the daemon, so only its client-side hooks take effect. For the same reason, `jvmTarget` is not set.
 */
object Jsr223DefaultScriptCompilationConfiguration : ScriptCompilationConfiguration(
    {
        refineConfiguration {
            beforeCompiling(::configureExposedJsr223Context)
            prependSyntheticSnippets(::generateBindingSnippetIfNeeded)
        }
        jsr223 {
            importAllBindings(true)
        }
    }
)

/** Supplies the `ScriptContext` and `ScriptTemplateWithBindings` instances of the implicit receivers. */
object Jsr223DefaultScriptEvaluationConfiguration : ScriptEvaluationConfiguration(
    {
        refineConfigurationBeforeEvaluate(::configureExposedJsr223Context)
    }
)
