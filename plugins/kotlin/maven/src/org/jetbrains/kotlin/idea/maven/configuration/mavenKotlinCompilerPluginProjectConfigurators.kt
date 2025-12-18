// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven.configuration

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.configuration.KotlinCompilerPluginProjectConfigurator
import org.jetbrains.kotlin.idea.maven.KotlinMavenBundle
import org.jetbrains.kotlin.idea.maven.PomFile
import org.jetbrains.kotlin.idea.maven.addKotlinCompilerPlugin
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.GROUP_ID
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.KOTLIN_VERSION_PROPERTY
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.findModulePomFile
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.kotlinPluginId
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand

abstract class AbstractMavenKotlinCompilerPluginProjectConfigurator(private val coroutineScope: CoroutineScope): KotlinCompilerPluginProjectConfigurator {
    override fun configureModule(module: Module): PsiFile? {
        val project = module.project
        val xmlFile = findModulePomFile(module) as? XmlFile ?: return null

        val pom = PomFile.forFileOrNull(xmlFile) ?: return null

        val mavenProjectsManager = MavenProjectsManager.getInstance(module.project)
        val mavenProject = mavenProjectsManager.findProject(module) ?: return null

        val kotlinPluginId = kotlinPluginId()
        val kotlinPlugin =
            mavenProject.plugins.find { it.mavenId.equals(kotlinPluginId.groupId, kotlinPluginId.artifactId) } ?: return null

        val execution =
            kotlinPlugin.executions.firstOrNull { it.goals.any { goalName -> goalName == PomFile.KotlinGoals.Compile } } ?: return null
        val configurationElement = execution.configurationElement

        val compilerPlugins = configurationElement?.getChild("compilerPlugins")
        if (compilerPlugins?.children?.any { it.name == "plugin" && it.text == kotlinPluginName } == true) return null

        coroutineScope.launchTracked {
            edtWriteAction {
                project.executeWriteCommand(KotlinMavenBundle.message("command.name.configure.0", xmlFile.name), null) {
                    pom.addKotlinCompilerPlugin(kotlinPluginName)?.let { kotlinPlugin ->
                        pluginDependencyMavenId?.let {
                            pom.addPluginDependency(kotlinPlugin, it)
                        }
                    }
                }
            }
        }
        return xmlFile
    }

    protected abstract val pluginDependencyMavenId: MavenId?

    protected abstract val kotlinPluginName: String
}

class SpringMavenKotlinCompilerPluginProjectConfigurator(coroutineScope: CoroutineScope): AbstractMavenKotlinCompilerPluginProjectConfigurator(coroutineScope) {
    override val kotlinPluginName: String
        get() = "spring"

    override val pluginDependencyMavenId: MavenId
        get() = MavenId(GROUP_ID, "kotlin-maven-allopen", $$"${$$KOTLIN_VERSION_PROPERTY}")

    override val compilerId: String = "kotlin-spring"

}