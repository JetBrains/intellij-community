// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiFile
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector
import org.jetbrains.kotlin.idea.configuration.addStdlibToJavaModuleInfo
import org.jetbrains.kotlin.idea.configuration.hasKotlinJvmRuntimeInScope
import org.jetbrains.kotlin.idea.maven.KotlinMavenBundle
import org.jetbrains.kotlin.idea.maven.PomFile
import org.jetbrains.kotlin.idea.versions.getDefaultJvmTarget
import org.jetbrains.kotlin.idea.versions.getStdlibArtifactId
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

class KotlinJavaMavenConfigurator : KotlinMavenConfigurator(TEST_LIB_ID, false, NAME, PRESENTABLE_TEXT) {

    override fun isKotlinModule(module: Module) =
        hasKotlinJvmRuntimeInScope(module)

    override fun isRelevantGoal(goalName: String) =
        goalName == PomFile.KotlinGoals.Compile

    override fun getStdlibArtifactId(module: Module, version: String): String =
        getStdlibArtifactId(ModuleRootManager.getInstance(module).sdk, version)

    override fun createExecutions(pomFile: PomFile, kotlinPlugin: MavenDomPlugin, module: Module) {
        createExecution(pomFile, kotlinPlugin, PomFile.DefaultPhases.Compile, PomFile.KotlinGoals.Compile, module, false)
        createExecution(pomFile, kotlinPlugin, PomFile.DefaultPhases.TestCompile, PomFile.KotlinGoals.TestCompile, module, true)
    }

    override fun configurePlugin(pom: PomFile, plugin: MavenDomPlugin, module: Module, version: String) {
        val sdk = ModuleRootManager.getInstance(module).sdk
        val jvmTarget = getDefaultJvmTarget(sdk, version)
        if (jvmTarget != null) {
            pom.addPluginConfiguration(plugin, "jvmTarget", jvmTarget.description)
        }
    }

    override fun configureModule(module: Module, file: PsiFile, version: String, collector: NotificationMessageCollector): Boolean {
        if (!super.configureModule(module, file, version, collector)) {
            return false
        }

        addStdlibToJavaModuleInfo(module, collector)
        return true
    }

    override val targetPlatform: TargetPlatform
        get() = JvmPlatforms.unspecifiedJvmPlatform

    @Suppress("DEPRECATION_ERROR")
    override fun getTargetPlatform() = JvmPlatforms.CompatJvmPlatform

    companion object {
        private const val NAME = "maven"
        const val TEST_LIB_ID = "kotlin-test"
        private val PRESENTABLE_TEXT get() = KotlinMavenBundle.message("configure.java.with.maven")
    }
}
