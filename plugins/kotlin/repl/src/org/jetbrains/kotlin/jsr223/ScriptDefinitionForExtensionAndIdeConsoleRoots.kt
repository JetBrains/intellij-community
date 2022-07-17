// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jsr223

import com.intellij.ide.extensionResources.ExtensionsRootType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.scratch.RootType
import com.intellij.ide.script.IdeConsoleRootType
import com.intellij.openapi.application.PathManager
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionSourceAsContributor
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import kotlin.io.path.nameWithoutExtension
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.templates.standard.ScriptTemplateWithBindings

object ScriptDefinitionForExtensionAndIdeConsoleRoots : ScriptDefinition.FromConfigurations(
    defaultJvmScriptingHostConfiguration,
    ScriptCompilationConfigurationForExtensionAndIdeConsoleRoots,
    ScriptEvaluationConfigurationForExtensionAndIdeConsoleRoots
) {
    override fun isScript(script: SourceCode): Boolean {
        val virtFileSourceCode = script as? VirtualFileScriptSource
        return virtFileSourceCode != null &&
                RootType.forFile(virtFileSourceCode.virtualFile)?.let {
                    it is ExtensionsRootType || it is IdeConsoleRootType
                } ?: false
    }
}

private const val SCRIPT_DEFINITION_NAME = "Script definition for extension scripts and IDE console"

// Deprecated API is used because actual one doesn't support this way of identifying scripts. Will be fixed eventually.
@Suppress("DEPRECATION")
class ScriptDefinitionForExtensionAndIdeConsoleRootsSource : ScriptDefinitionSourceAsContributor {
    override val id: String = SCRIPT_DEFINITION_NAME

    override val definitions: Sequence<ScriptDefinition>
        get() = sequenceOf(ScriptDefinitionForExtensionAndIdeConsoleRoots)
}

private object ScriptCompilationConfigurationForExtensionAndIdeConsoleRoots : ScriptCompilationConfiguration(
    {
        baseClass(KotlinType(ScriptTemplateWithBindings::class))
        displayName(SCRIPT_DEFINITION_NAME)
        jvm {
            val kotlincLibraryName =
                PathManager.getJarForClass(KotlinVersion::class.java)?.nameWithoutExtension
                    ?: error("unable to locate Kotlin standard library")
            // This approach works, but could be quite expensive, since it forces indexing of the whole IDEA classpath
            // more economical approach would be to list names (without versions and .jar extension) of all jars
            // required for the scripts after the kotlin stdlib/script-runtime, and set wholeClasspath to false
            dependenciesFromClassContext(
                ScriptCompilationConfigurationForExtensionAndIdeConsoleRoots::class,
                kotlincLibraryName,
                wholeClasspath = true
            )
            PluginManagerCore.getPlugins().forEach {
                dependenciesFromClassloader(classLoader = it.classLoader, wholeClasspath = true)
            }
        }
        ide {
            acceptedLocations(ScriptAcceptedLocation.Everywhere)
        }
    }
)

private object ScriptEvaluationConfigurationForExtensionAndIdeConsoleRoots : ScriptEvaluationConfiguration({})

