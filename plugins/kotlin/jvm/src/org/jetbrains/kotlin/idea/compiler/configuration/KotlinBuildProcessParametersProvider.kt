// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.IncrementalCompilation
import org.jetbrains.kotlin.idea.PluginStartupApplicationService

class KotlinBuildProcessParametersProvider(private val project: Project) : BuildProcessParametersProvider() {

    private val MIN_DEP_GRAPH_SUPPORTING_VERSION = IdeKotlinVersion.opt("1.9.24")!!

    override fun getVMArguments(): List<String> {
        val compilerWorkspaceSettings = KotlinCompilerWorkspaceSettings.getInstance(project)
        val arguments = mutableListOf<String>()

        if (compilerWorkspaceSettings.preciseIncrementalEnabled) {
            arguments += "-D" + IncrementalCompilation.INCREMENTAL_COMPILATION_JVM_PROPERTY + "=true"

            if (AdvancedSettings.getBoolean("compiler.unified.ic.implementation")) {
                val configuredKotlinVersion = IdeKotlinVersion.opt(KotlinJpsPluginSettings.jpsVersion(project))
                if (configuredKotlinVersion != null && configuredKotlinVersion.compareTo(MIN_DEP_GRAPH_SUPPORTING_VERSION) >= 0) {
                    arguments += "-Dkotlin.jps.dumb.mode=true"
                }
            }
        }

        if (compilerWorkspaceSettings.incrementalCompilationForJsEnabled) {
            arguments += "-D" + IncrementalCompilation.INCREMENTAL_COMPILATION_JS_PROPERTY + "=true"
        }

        if (compilerWorkspaceSettings.enableDaemon) {
            arguments += "-Dkotlin.daemon.enabled"
        }

        if (compilerWorkspaceSettings.daemonVmOptions.isNotEmpty()) {
            compilerWorkspaceSettings.daemonVmOptions.split(" ").forEach {
                arguments += it
            }
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
