// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION", "IO_FILE_USAGE")

package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.core.script.scratch.definition.KotlinScratchCompilationConfiguration
import org.jetbrains.kotlin.idea.core.script.shared.definition.jdkSupplier
import org.jetbrains.kotlin.idea.core.script.shared.definition.scriptClassPath
import org.jetbrains.kotlin.idea.core.script.v1.ScratchFileOptionsByFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.defaultScratchJavaHome
import org.jetbrains.kotlin.idea.jvm.shared.scratch.isKotlinScratch
import org.jetbrains.kotlin.idea.jvm.shared.scratch.scratchModuleSdkHome
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import java.io.File
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.displayName
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

class KotlinScratchDefinitionSource(val project: Project) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition>
        get() = sequenceOf(
            object : ScriptDefinition.FromConfigurations(
                defaultJvmScriptingHostConfiguration,
                createCompilationConfiguration(),
                null
            ) {
                override fun isScript(script: SourceCode): Boolean {
                    val virtualFile = (script as? VirtualFileScriptSource)?.virtualFile ?: return false
                    return virtualFile.isKotlinScratch
                }
            }
        )

    private fun createCompilationConfiguration(): ScriptCompilationConfiguration = ScriptCompilationConfiguration(
        listOf(KotlinScratchCompilationConfiguration)
    ) {
        dependencies(JvmDependency(scriptClassPath))
        displayName("Kotlin Scratch")
        hostConfiguration(defaultJvmScriptingHostConfiguration)
        ide.dependenciesSources(JvmDependency(KotlinArtifacts.kotlinStdlibSources))
        ide.jdkSupplier { virtualFile ->
            getScriptJdk(virtualFile)
        }
    }

    private fun getScriptJdk(virtualFile: VirtualFile): File? {
        val jdkHome = scratchModuleSdkHome(project, virtualFile)
            ?: ScratchFileOptionsByFile[project, virtualFile]?.selectedJdkHome
            ?: defaultScratchJavaHome
        return jdkHome?.takeIf { it.isNotBlank() }?.let(::File)
    }
}
