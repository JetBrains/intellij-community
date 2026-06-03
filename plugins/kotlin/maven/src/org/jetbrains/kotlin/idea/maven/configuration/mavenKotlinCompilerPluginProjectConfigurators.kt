// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven.configuration

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.openapi.module.Module
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin
import org.jetbrains.idea.maven.dom.model.MavenDomPluginExecution
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.allopen.AllOpenPluginNames
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.ConfigurationResultBuilder
import org.jetbrains.kotlin.idea.configuration.KotlinCompilerPluginProjectConfigurator
import org.jetbrains.kotlin.idea.configuration.KotlinDependencyProvider
import org.jetbrains.kotlin.idea.maven.KotlinMavenBundle
import org.jetbrains.kotlin.idea.maven.PomFile
import org.jetbrains.kotlin.idea.maven.addKotlinCompilerPlugin
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.GROUP_ID
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.KOTLIN_VERSION_PROPERTY
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.findModulePomFile
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.findPomXmlByFile
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.kotlinPluginId
import org.jetbrains.kotlin.idea.maven.createChildTag
import org.jetbrains.kotlin.idea.maven.findSubTagOrCreate
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import java.nio.file.Files

abstract class AbstractMavenKotlinCompilerPluginProjectConfigurator: KotlinCompilerPluginProjectConfigurator {
    override fun isApplicable(module: Module): Boolean =
        module.findSuitablePomFileWithPlugin(kotlinPluginId) != null

    override fun configureModule(module: Module, configurationResultBuilder: ConfigurationResultBuilder) {
        val project = module.project
        val xmlFile = module.findSuitablePomFileWithPlugin(kotlinPluginId) ?: return
        configurationResultBuilder.changedFile(xmlFile)

        val pom = PomFile.forFileOrNull(xmlFile) ?: return
        val kotlinPlugin = pom.findPlugin(kotlinPluginId) ?: return

        val execution = kotlinPlugin.findExecutionWithKotlinCompileGoal() ?: return

        val configurationElement = execution.configuration.ensureTagExists()
        val compilerPlugins = configurationElement.findSubTags("compilerPlugins").firstOrNull()

        if (compilerPlugins?.findSubTags("plugin")?.firstOrNull { it.value.trimmedText == kotlinCompilerPluginId } != null) return

        project.executeWriteCommand(KotlinMavenBundle.message("command.name.configure.0", xmlFile.name), null) {
            pom.addKotlinCompilerPlugin(kotlinCompilerPluginId)?.let { kotlinPlugin ->
                pluginDependencyMavenId?.let {
                    pom.addPluginDependency(kotlinPlugin, it)
                }
                pom.customizeKotlinPlugin(kotlinPlugin, module)
                configurationResultBuilder.configuredModule(module)
            }
        }
    }

    protected open fun PomFile.customizeKotlinPlugin(
        kotlinPlugin: MavenDomPlugin,
        module: Module
    ) {
    }

    override fun configureModuleModCommand(module: Module): ModCommand {
        val kotlinPluginId = kotlinPluginId
        val xmlFile = module.findSuitablePomFileWithPlugin(kotlinPluginId) ?: return ModCommand.nop()
        val actionContext = ActionContext.from(null, xmlFile)
        return ModCommand.psiUpdate(actionContext) { updater ->
            val writablePomFile = updater.getWritable(xmlFile)
            val pom = PomFile.forFileOrNull(writablePomFile) ?: return@psiUpdate
            val kotlinPlugin = pom.findPlugin(kotlinPluginId) ?: return@psiUpdate

            val execution = kotlinPlugin.findExecutionWithKotlinCompileGoal()

            val configurationElement =
                (execution?.configuration ?: kotlinPlugin.configuration).ensureTagExists()
            val compilerPlugins = configurationElement.findSubTags("compilerPlugins").firstOrNull()

            if (compilerPlugins?.findSubTags("plugin")?.firstOrNull { it.value.trimmedText == kotlinCompilerPluginId } != null)
                return@psiUpdate

            pom.addKotlinCompilerPlugin(kotlinCompilerPluginId)?.let { kotlinPlugin ->
                pluginDependencyMavenId?.let {
                    pom.addPluginDependency(kotlinPlugin, it)
                }
                pom.customizeKotlinPlugin(kotlinPlugin, module)
            }
        }.andThen(KotlinDependencyProvider.syncModCommand(xmlFile))
    }

    protected abstract val pluginDependencyMavenId: MavenId?

    private fun MavenDomPlugin.findExecutionWithKotlinCompileGoal(): MavenDomPluginExecution? =
        executions.executions.firstOrNull { execution: MavenDomPluginExecution ->
            execution.goals.goals.any {
                it.rawText == PomFile.KotlinGoals.Compile
            }
        }

    private fun XmlFile.hasSuitablePlugin(pluginId: MavenId, extraCheck: (MavenDomPlugin) -> Boolean = { true }): Boolean {
        val plugin = PomFile.forFileOrNull(this)
            ?.findPlugin(pluginId) ?: return false
        return extraCheck(plugin)
    }

    private fun Module.findSuitablePomFileWithPlugin(pluginId: MavenId): XmlFile? {
        // try to find suitable maven kotlin plugin in current module pom file
        val pomFile = findModulePomFile(this) ?: return null
        if (pomFile.hasSuitablePlugin(pluginId, extraCheck = { it.findExecutionWithKotlinCompileGoal() != null })) return pomFile

        val project = this.project
        val mavenProjectsManager = MavenProjectsManager.getInstance(project)
        val mavenProject = mavenProjectsManager.findProject(this)

        if (mavenProject != null) {
            var parentId: MavenId? = mavenProject.parentId

            // child module could inherit plugin from parent
            while (parentId != null) {
                val parentModule = mavenProjectsManager.findProject(parentId) ?: break
                val psiFile = findPomXmlByFile(parentModule.file) ?: break
                if (psiFile.hasSuitablePlugin(pluginId, extraCheck = { it.findExecutionWithKotlinCompileGoal() != null })) {
                    return psiFile
                }
                parentId = parentModule.parentId
            }
        }

        // otherwise, force using pom.xml for the current module if it has declared maven kotlin plugin
        return pomFile.takeIf { it.hasSuitablePlugin(pluginId) }
    }
}

class SpringMavenKotlinCompilerPluginProjectConfigurator : AbstractMavenKotlinCompilerPluginProjectConfigurator() {

    override val kotlinCompilerPluginId: String = "spring"

    override val pluginDependencyMavenId: MavenId
        get() = MavenId(GROUP_ID, "kotlin-maven-allopen", $$"${$$KOTLIN_VERSION_PROPERTY}")

}

class LombokMavenKotlinCompilerPluginProjectConfigurator : AbstractMavenKotlinCompilerPluginProjectConfigurator() {

    override val kotlinCompilerPluginId: String = "lombok"

    override val pluginDependencyMavenId: MavenId
        get() = MavenId(GROUP_ID, "kotlin-maven-lombok", $$"${$$KOTLIN_VERSION_PROPERTY}")

    override fun PomFile.customizeKotlinPlugin(kotlinPlugin: MavenDomPlugin, module: Module) {
        val configPath = module.findLombokConfigPath() ?: return
        val configurationElement = kotlinPlugin.configuration.ensureTagExists()
        val pluginOptions = configurationElement.findSubTagOrCreate("pluginOptions")
        val option = "lombok:config=$configPath"
        if (pluginOptions.findSubTags("option").any { it.value.text == option }) return

        pluginOptions.add(pluginOptions.createChildTag("option", option))
    }

}

class JpaMavenKotlinCompilerPluginProjectConfigurator : AbstractMavenKotlinCompilerPluginProjectConfigurator() {

    override val kotlinCompilerPluginId: String = "jpa"

    override val pluginDependencyMavenId: MavenId
        get() = MavenId(GROUP_ID, "kotlin-maven-noarg", $$"${$$KOTLIN_VERSION_PROPERTY}")

    override fun PomFile.customizeKotlinPlugin(kotlinPlugin: MavenDomPlugin, module: Module) {
        val propertyTag = this.findProperty(KOTLIN_VERSION_PROPERTY) ?: return
        val version = IdeKotlinVersion.get(propertyTag.value.text)
        if (version.kotlinVersion.isAtLeast(2, 3, 20)) return

        addAllOpenKotlinCompilerPluginPreset(kotlinPlugin, kotlinCompilerPluginId)
    }
}

private fun Module.findLombokConfigPath(): String? {
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    val mavenProject = mavenProjectsManager.findProject(this) ?: return null
    val moduleConfig = mavenProject.directoryPath.resolve("lombok.config")
    if (Files.exists(moduleConfig)) return "lombok.config"

    val parentConfig = mavenProject.parentId
        ?.let(mavenProjectsManager::findProject)
        ?.directoryPath
        ?.resolve("lombok.config")
        ?.takeIf(Files::exists)
    return parentConfig?.toString()
}

internal fun PomFile.addAllOpenKotlinCompilerPluginPreset(kotlinPlugin: MavenDomPlugin, kotlinCompilerPluginId: String) {
    val allOpenPluginName = "all-open"

    val allOpenPluginMavenId = MavenId(GROUP_ID, "kotlin-maven-allopen", $$"${$$KOTLIN_VERSION_PROPERTY}")
    addKotlinCompilerPlugin(allOpenPluginName)
    addPluginDependency(kotlinPlugin, allOpenPluginMavenId)

    val configurationElement = kotlinPlugin.configuration.ensureTagExists()
    val pluginOptions = configurationElement.findSubTagOrCreate("pluginOptions")
    val options = AllOpenPluginNames.SUPPORTED_PRESETS[kotlinCompilerPluginId] ?: return
    val optionTagName = "option"

    for (option in options) {
        val value = "${allOpenPluginName}:${AllOpenPluginNames.ANNOTATION_OPTION_NAME}=$option"
        val firstOrNull = pluginOptions.findSubTags(optionTagName).firstOrNull { it.value.text == value }
        firstOrNull?.let { continue }

        val optionTag = pluginOptions.createChildTag(optionTagName, value)
        pluginOptions.add(optionTag)
    }
}