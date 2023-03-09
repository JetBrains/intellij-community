// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package org.jetbrains.kotlin.idea.script.definition

import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.ScriptingHostConfigurationKeys
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.util.PropertiesCollection


val ScriptingHostConfigurationKeys.getEnvironment by PropertiesCollection.key<() -> Map<String, Any?>?>()

@KotlinScript(
    displayName = "Definition for a.kts",
    fileExtension = "a.kts",
    compilationConfiguration = TestScriptCompilationConfigurationA::class
)
class TestScriptDefinitionA(val args: Array<String>)


@KotlinScript(
    displayName = "Definition for b.kts",
    fileExtension = "b.kts",
    compilationConfiguration = TestScriptCompilationConfigurationB::class
)
class TestScriptDefinitionB(val args: Array<String>)


@Suppress("UNCHECKED_CAST")
object TestScriptCompilationConfigurationA : ScriptCompilationConfiguration(
    {
        ide {
            acceptedLocations(ScriptAcceptedLocation.Everywhere)
        }

        refineConfiguration {
            beforeCompiling { context ->
                val environment = context.compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]?.let {
                    it[ScriptingHostConfiguration.getEnvironment]?.invoke()
                }.orEmpty()

                context.compilationConfiguration.with {
                    dependencies(JvmDependency(environment["lib-classes-A"] as List<File>))
                }.asSuccess()
            }
        }
    }
)

@Suppress("UNCHECKED_CAST")
object TestScriptCompilationConfigurationB : ScriptCompilationConfiguration(
    {
        ide {
            acceptedLocations(ScriptAcceptedLocation.Everywhere)
        }

        refineConfiguration {
            beforeCompiling { context ->
                val environment = context.compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration]?.let {
                    it[ScriptingHostConfiguration.getEnvironment]?.invoke()
                }.orEmpty()

                context.compilationConfiguration.with {
                    dependencies(JvmDependency(environment["lib-classes-B"] as List<File>))
                }.asSuccess()
            }
        }
    }
)