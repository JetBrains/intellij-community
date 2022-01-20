// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.io.isDirectory
import org.jetbrains.kotlin.config.IncrementalCompilation
import org.jetbrains.kotlin.idea.PluginStartupApplicationService
import org.jetbrains.kotlin.idea.jps.SetupKotlinJpsPluginBeforeCompileTask
import java.nio.file.Path

class KotlinBuildProcessParametersProvider(private val project: Project) : BuildProcessParametersProvider() {
    override fun getVMArguments(): MutableList<String> {
        val compilerWorkspaceSettings = KotlinCompilerWorkspaceSettings.getInstance(project)

        val res = arrayListOf<String>()
        if (compilerWorkspaceSettings.preciseIncrementalEnabled) {
            res.add("-D" + IncrementalCompilation.INCREMENTAL_COMPILATION_JVM_PROPERTY + "=true")
        }
        if (compilerWorkspaceSettings.incrementalCompilationForJsEnabled) {
            res.add("-D" + IncrementalCompilation.INCREMENTAL_COMPILATION_JS_PROPERTY + "=true")
        }
        if (compilerWorkspaceSettings.enableDaemon) {
            res.add("-Dkotlin.daemon.enabled")
        }
        if (Registry.`is`("kotlin.jps.instrument.bytecode", false)) {
            res.add("-Dkotlin.jps.instrument.bytecode=true")
        }
        PluginStartupApplicationService.getInstance().aliveFlagPath.let {
            if (!it.isBlank()) {
                // TODO: consider taking the property name from compiler/daemon/common (check whether dependency will be not too heavy)
                res.add("-Dkotlin.daemon.client.alive.path=\"$it\"")
            }
        }
        return res
    }

    override fun getPathParameters(): List<Pair<String, Path>> =
        listOfNotNull(
            Pair("-Djps.kotlin.home=", KotlinPathsProvider.getKotlinPaths(project).homePath.toPath()).takeIf { it.second.isDirectory() }
        )
}
