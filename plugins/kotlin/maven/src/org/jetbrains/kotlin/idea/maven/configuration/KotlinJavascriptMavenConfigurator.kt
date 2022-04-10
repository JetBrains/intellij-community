// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.maven.configuration

import com.intellij.openapi.module.Module
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.hasKotlinJsRuntimeInScope
import org.jetbrains.kotlin.idea.maven.KotlinMavenBundle
import org.jetbrains.kotlin.idea.maven.PomFile
import org.jetbrains.kotlin.idea.versions.MAVEN_JS_STDLIB_ID
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms

class KotlinJavascriptMavenConfigurator : KotlinMavenConfigurator(null, false, NAME, PRESENTABLE_TEXT) {

    override fun getStdlibArtifactId(module: Module, version: IdeKotlinVersion) = MAVEN_JS_STDLIB_ID

    override fun isKotlinModule(module: Module): Boolean {
        return hasKotlinJsRuntimeInScope(module)
    }

    override fun isRelevantGoal(goalName: String): Boolean {
        return goalName == PomFile.KotlinGoals.Js
    }

    override fun createExecutions(pomFile: PomFile, kotlinPlugin: MavenDomPlugin, module: Module) {
        createExecution(pomFile, kotlinPlugin, PomFile.DefaultPhases.Compile, PomFile.KotlinGoals.Js, module, false)
        createExecution(pomFile, kotlinPlugin, PomFile.DefaultPhases.TestCompile, PomFile.KotlinGoals.TestJs, module, true)
    }

    override val targetPlatform: TargetPlatform
        get() = JsPlatforms.defaultJsPlatform

    override fun getMinimumSupportedVersion() = "1.1.0"

    companion object {
        private const val NAME = "js maven"
        private val PRESENTABLE_TEXT get() = KotlinMavenBundle.message("configure.javascript.with.maven")
    }
}
