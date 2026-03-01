// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven.configuration

import com.intellij.openapi.module.Module
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.maven.dom.model.MavenDomPluginExecution
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.kotlin.idea.configuration.ConfigurationResultBuilder
import org.jetbrains.kotlin.idea.configuration.KotlinCompilerPluginProjectConfigurator
import org.jetbrains.kotlin.idea.maven.KotlinMavenBundle
import org.jetbrains.kotlin.idea.maven.PomFile
import org.jetbrains.kotlin.idea.maven.addKotlinCompilerPlugin
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.GROUP_ID
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.KOTLIN_VERSION_PROPERTY
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.findModulePomFile
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.kotlinPluginId
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand

abstract class AbstractMavenKotlinCompilerPluginProjectConfigurator: KotlinCompilerPluginProjectConfigurator {
    override fun isApplicable(module: Module): Boolean {
        val xmlFile = findModulePomFile(module) as? XmlFile ?: return false
        return PomFile.forFileOrNull(xmlFile) != null
    }

    override fun configureModule(module: Module, configurationResultBuilder: ConfigurationResultBuilder) {
        val project = module.project
        val xmlFile = findModulePomFile(module) as? XmlFile ?: return
        configurationResultBuilder.changedFile(xmlFile)

        val pom = PomFile.forFileOrNull(xmlFile) ?: return

        val kotlinPlugin = pom.findPlugin(kotlinPluginId(null)) ?: return

        val execution =
            kotlinPlugin.executions.executions.firstOrNull { execution: MavenDomPluginExecution ->
                execution.goals.goals.any {
                    it.rawText == PomFile.KotlinGoals.Compile
                }
            } ?: return

        val configurationElement = execution.configuration.ensureTagExists()
        val compilerPlugins = configurationElement.findSubTags("compilerPlugins").firstOrNull()

        if (compilerPlugins?.findSubTags("plugin")?.firstOrNull { it.value.trimmedText == kotlinCompilerPluginId } != null) return

        project.executeWriteCommand(KotlinMavenBundle.message("command.name.configure.0", xmlFile.name), null) {
            pom.addKotlinCompilerPlugin(kotlinCompilerPluginId)?.let { kotlinPlugin ->
                pluginDependencyMavenId?.let {
                    pom.addPluginDependency(kotlinPlugin, it)
                }
                configurationResultBuilder.configuredModule(module)
            }
        }
    }

    protected abstract val pluginDependencyMavenId: MavenId?
}

class SpringMavenKotlinCompilerPluginProjectConfigurator : AbstractMavenKotlinCompilerPluginProjectConfigurator() {

    override val kotlinCompilerPluginId: String = "spring"

    override val pluginDependencyMavenId: MavenId
        get() = MavenId(GROUP_ID, "kotlin-maven-allopen", $$"${$$KOTLIN_VERSION_PROPERTY}")

}

class JpaMavenKotlinCompilerPluginProjectConfigurator : AbstractMavenKotlinCompilerPluginProjectConfigurator() {

    override val kotlinCompilerPluginId: String = "jpa"

    override val pluginDependencyMavenId: MavenId
        get() = MavenId(GROUP_ID, "kotlin-maven-noarg", $$"${$$KOTLIN_VERSION_PROPERTY}")

}