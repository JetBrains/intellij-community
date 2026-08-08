// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION", "IO_FILE_USAGE")

package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.core.script.scratch.definition.KotlinScratchCompilationConfiguration
import org.jetbrains.kotlin.idea.core.script.shared.definition.jdkSupplier
import org.jetbrains.kotlin.idea.core.script.shared.definition.scriptClassPath
import org.jetbrains.kotlin.idea.core.script.v1.ScratchFileOptionsByFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.defaultScratchJavaHome
import org.jetbrains.kotlin.idea.jvm.shared.scratch.scratchModuleSdkHome
import java.io.File
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.displayName
import kotlin.script.experimental.api.filePathPattern
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

class KotlinScratchDefinitionProvider(val project: Project) : ScriptDefinitionsProvider {
    override val id: String = "KotlinScratch"

    override fun provideDefinitions(
        baseHostConfiguration: ScriptingHostConfiguration,
        loadedScriptDefinitions: List<ScriptDefinition>,
    ): List<ScriptDefinition> = listOf(
        ScriptDefinition(createCompilationConfiguration(), ScriptEvaluationConfiguration.Default)
    )

    private fun createCompilationConfiguration(): ScriptCompilationConfiguration = ScriptCompilationConfiguration(
        listOf(KotlinScratchCompilationConfiguration)
    ) {
        dependencies(JvmDependency(scriptClassPath))
        displayName("Kotlin Scratch")
        hostConfiguration(defaultJvmScriptingHostConfiguration)
        ide.dependenciesSources(JvmDependency(KotlinArtifacts.kotlinStdlibSources))
        // only .kts under the scratches root, e.g. <config>/scratches/scratch_174.kts; the extension is checked separately
        filePathPattern(scratchPathPattern())
        ide.jdkSupplier { virtualFile ->
            val jdkHome = scratchModuleSdkHome(project, virtualFile)
                ?: ScratchFileOptionsByFile[project, virtualFile].selectedJdkHome
                ?: defaultScratchJavaHome
            jdkHome?.takeIf { it.isNotBlank() }?.let(::File)
        }
    }

    /**
     * `isScript` matches against [VirtualFile.path], so the root has to be resolved through the VFS too — a plain
     * config path can differ from it by symlink canonicalization (`/var` vs `/private/var` on macOS).
     * Until the root shows up in the VFS, fall back to matching the directory name at any depth.
     */
    private fun scratchPathPattern(): String {
        val root = ScratchFileService.getInstance().getRootPath(ScratchRootType.getInstance())
        val vfsRoot = LocalFileSystem.getInstance().findFileByPath(FileUtilRt.toSystemIndependentName(root))
            ?: return ".*/${Regex.escape(PathUtil.getFileName(root))}/.*"

        return "${Regex.escape(vfsRoot.path)}/.*"
    }
}
