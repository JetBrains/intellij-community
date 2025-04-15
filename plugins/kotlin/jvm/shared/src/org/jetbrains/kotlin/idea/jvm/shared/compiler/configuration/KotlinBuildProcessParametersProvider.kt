// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.compiler.configuration

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.config.IncrementalCompilation
import org.jetbrains.kotlin.idea.PluginStartupApplicationService
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerWorkspaceSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import java.nio.file.Path

class KotlinBuildProcessParametersProvider(private val project: Project) : BuildProcessParametersProvider() {

    private val MIN_DEP_GRAPH_SUPPORTING_VERSION = IdeKotlinVersion.opt("1.9.24")!!

    override fun getVMArguments(): List<String> {
        val compilerWorkspaceSettings = KotlinCompilerWorkspaceSettings.getInstance(project)
        val arguments = mutableListOf<String>()

        if (compilerWorkspaceSettings.preciseIncrementalEnabled) {
            arguments += "-D" + IncrementalCompilation.INCREMENTAL_COMPILATION_JVM_PROPERTY + "=true"

            if (AdvancedSettings.getBoolean("compiler.unified.ic.implementation") && !Registry.`is`("compiler.process.use.portable.caches")) {
                val configuredKotlinVersion = IdeKotlinVersion.opt(KotlinJpsPluginSettings.jpsVersion(project))
                if (configuredKotlinVersion != null && configuredKotlinVersion.compareTo(MIN_DEP_GRAPH_SUPPORTING_VERSION) >= 0) {
                    arguments += "-Dkotlin.jps.dumb.mode=true"
                    arguments += "-Dkotlin.jps.enable.lookups.in.dumb.mode=true"
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

        return arguments
    }

    override fun getPathParameters(): List<Pair<String, Path>> = buildList {
        PluginStartupApplicationService.getInstance().getAliveFlagPath().let {
            if (it.isNotBlank()) {
                add(Pair("-Dkotlin.daemon.client.alive.path=", Path.of(it)))
            }
        }
    }
}
