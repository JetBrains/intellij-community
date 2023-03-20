// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.IncrementalCompilation
import org.jetbrains.kotlin.idea.PluginStartupApplicationService

class KotlinBuildProcessParametersProvider(private val project: Project) : BuildProcessParametersProvider() {
    override fun getVMArguments(): List<String> {
        val compilerWorkspaceSettings = KotlinCompilerWorkspaceSettings.getInstance(project)
        val arguments = mutableListOf<String>()

        if (compilerWorkspaceSettings.preciseIncrementalEnabled) {
            arguments += "-D" + IncrementalCompilation.INCREMENTAL_COMPILATION_JVM_PROPERTY + "=true"
        }

        if (compilerWorkspaceSettings.incrementalCompilationForJsEnabled) {
            arguments += "-D" + IncrementalCompilation.INCREMENTAL_COMPILATION_JS_PROPERTY + "=true"
        }

        if (compilerWorkspaceSettings.enableDaemon) {
            arguments += "-Dkotlin.daemon.enabled"
        }

        PluginStartupApplicationService.getInstance().getAliveFlagPath().let {
            if (!it.isBlank()) {
                // TODO: consider taking the property name from compiler/daemon/common (check whether dependency will be not too heavy)
                arguments += "-Dkotlin.daemon.client.alive.path=\"$it\""
            }
        }

        return arguments
    }
}
