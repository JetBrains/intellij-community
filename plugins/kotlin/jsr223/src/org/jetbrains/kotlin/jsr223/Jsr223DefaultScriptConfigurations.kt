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
 * The compilation configuration every snippet of [KotlinJsr223DaemonScriptEngineImpl] is compiled
 * with -- the counterpart of the Kotlin project's own
 * `kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptCompilationConfiguration` (in the
 * `kotlin-scripting-jsr223` artifact, which is not published for the IDE).
 *
 * Unlike that one, this is *not* attached to a `@KotlinScript`-annotated template class: on the
 * out-of-process compile path the definition never reaches the compile daemon anyway (see
 * [KotlinJsr223DaemonScriptEngineImpl]'s KDoc), so only the parts that run **client-side** are
 * meaningful here -- namely the two bindings-exposure hooks:
 *  * `beforeCompiling` -> [configureExposedJsr223Context] adds the `ScriptContext`/
 *    `ScriptTemplateWithBindings` implicit receivers, whose *type names* [DaemonReplCompiler]
 *    forwards to the daemon through a dedicated CLI option;
 *  * `prependSyntheticSnippets` -> [generateBindingSnippetIfNeeded] generates the synthetic snippet
 *    that declares every JSR-223 binding as a typed property.
 *
 * A `jvm { jvmTarget(...) }` entry is deliberately *not* set here: it would have no effect, since
 * this configuration is never fed into the daemon's own compile arguments.
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

/**
 * The evaluation configuration every compiled snippet is evaluated with: supplies the actual
 * implicit-receiver *instances* (the live `ScriptContext` and a `ScriptTemplateWithBindings` view
 * over its `ENGINE_SCOPE` bindings) matching the receiver types declared at compile time.
 */
object Jsr223DefaultScriptEvaluationConfiguration : ScriptEvaluationConfiguration(
    {
        refineConfigurationBeforeEvaluate(::configureExposedJsr223Context)
    }
)
