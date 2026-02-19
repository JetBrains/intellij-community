// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.gradle

import com.intellij.openapi.diagnostic.Logger
import kotlinx.collections.immutable.toPersistentList
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.compatibility.GradleToPluginsCompatibilityStore
import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.PluginSettingsOwner
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.UNIT_SUCCESS
import org.jetbrains.kotlin.tools.projectWizard.core.asPath
import org.jetbrains.kotlin.tools.projectWizard.core.asSuccess
import org.jetbrains.kotlin.tools.projectWizard.core.buildPersistenceList
import org.jetbrains.kotlin.tools.projectWizard.core.checker
import org.jetbrains.kotlin.tools.projectWizard.core.div
import org.jetbrains.kotlin.tools.projectWizard.core.entity.PipelineTask
import org.jetbrains.kotlin.tools.projectWizard.core.entity.properties.Property
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.PluginSetting
import org.jetbrains.kotlin.tools.projectWizard.core.safeAs
import org.jetbrains.kotlin.tools.projectWizard.core.service.FileSystemWizardService
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.AllProjectsRepositoriesIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildSystemIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildSystemPluginIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.FoojayPluginIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.KotlinBuildSystemPluginIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.MultiplatformSourcesetIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.PluginManagementRepositoryIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.RepositoryIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.distinctAndSorted
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.SettingsGradleFileIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.render
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.withIrs
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.StructurePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildFileData
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.allModulesPaths
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.buildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.getPluginRepositoriesWithDefaultOnes
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.isGradle
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.KotlinPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.GradlePrinter
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.printBuildFile
import org.jetbrains.kotlin.tools.projectWizard.plugins.projectPath
import org.jetbrains.kotlin.tools.projectWizard.plugins.templates.TemplatesPlugin
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.updateBuildFiles
import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version
import org.jetbrains.kotlin.tools.projectWizard.templates.FileTemplate
import org.jetbrains.kotlin.tools.projectWizard.templates.FileTemplateDescriptor
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.isFoojayPluginSupported


abstract class GradlePlugin(context: Context) : BuildSystemPlugin(context) {
    override val path = pluginPath

    companion object : PluginSettingsOwner() {

        private val LOG = Logger.getInstance(GradlePlugin::class.java)

        override val pluginPath = "buildSystem.gradle"

        val gradleProperties by listProperty(
            "kotlin.code.style" to "official"
        )

        val settingsGradleFileIRs by listProperty<BuildSystemIR>()

        val createGradlePropertiesFile by pipelineTask(GenerationPhase.PROJECT_GENERATION) {
            runAfter(KotlinPlugin.createModules)
            runBefore(TemplatesPlugin.renderFileTemplates)
            isAvailable = isGradle
            withAction {
                TemplatesPlugin.addFileTemplate.execute(
                    FileTemplate(
                        FileTemplateDescriptor(
                            "gradle/gradle.properties.vm",
                            "gradle.properties".asPath()
                        ),
                        StructurePlugin.projectPath.settingValue,
                        mapOf(
                            "properties" to gradleProperties
                                .propertyValue
                                .distinctBy { it.first }
                        )
                    )
                )
            }
        }

        val localProperties by listProperty<Pair<String, String>>()

        val createLocalPropertiesFile by pipelineTask(GenerationPhase.PROJECT_GENERATION) {
            runAfter(KotlinPlugin.createModules)
            runBefore(TemplatesPlugin.renderFileTemplates)
            isAvailable = isGradle
            withAction {
                val properties = localProperties.propertyValue
                if (properties.isEmpty()) return@withAction UNIT_SUCCESS
                TemplatesPlugin.addFileTemplate.execute(
                    FileTemplate(
                        FileTemplateDescriptor(
                            "gradle/local.properties.vm",
                            "local.properties".asPath()
                        ),
                        StructurePlugin.projectPath.settingValue,
                        mapOf(
                            "properties" to localProperties.propertyValue
                        )
                    )
                )
            }
        }

        private val isGradle = checker { buildSystemType.isGradle }

        private val isGradleWrapper = checker {
            buildSystemType.isGradle && gradleHome.settingValue == ""
        }

        val gradleVersion by valueSetting(
            "<GRADLE_VERSION>",
            GenerationPhase.PROJECT_GENERATION,
            parser = Version.parser
        ) {
            defaultValue = value(Versions.GRADLE)
        }

        val gradleHome by stringSetting(
            "<GRADLE_HOME>",
            GenerationPhase.PROJECT_GENERATION,
        ) {
            defaultValue = value("")
        }

        val initGradleWrapperTask by pipelineTask(GenerationPhase.PROJECT_GENERATION) {
            runBefore(TemplatesPlugin.renderFileTemplates)
            isAvailable = isGradleWrapper
            withAction {
                TemplatesPlugin.addFileTemplate.execute(
                    FileTemplate(
                        FileTemplateDescriptor(
                            "gradle/gradle-wrapper.properties.vm",
                            "gradle" / "wrapper" / "gradle-wrapper.properties"
                        ),
                        StructurePlugin.projectPath.settingValue,
                        mapOf(
                            "version" to gradleVersion.settingValue
                        )
                    )
                )
            }
        }

        val mergeCommonRepositories by pipelineTask(GenerationPhase.PROJECT_GENERATION) {
            runBefore(createModules)
            runAfter(takeRepositoriesFromDependencies)
            runAfter(KotlinPlugin.createPluginRepositories)

            isAvailable = isGradle

            withAction {
                val buildFiles = buildFiles.propertyValue
                if (buildFiles.size == 1) return@withAction UNIT_SUCCESS
                val moduleRepositories = buildFiles.mapNotNull { buildFileIR ->
                    if (buildFileIR.isRoot) null
                    else buildFileIR.irs.mapNotNull { it.safeAs<RepositoryIR>()?.repository }
                }

                val allRepositories = moduleRepositories.flatMapTo(hashSetOf()) { it }

                val commonRepositories = allRepositories.filterTo(
                    KotlinPlugin.version.propertyValue.repositories.toMutableSet()
                ) { repo ->
                    moduleRepositories.all { repo in it }
                }

                updateBuildFiles { buildFile ->
                    buildFile.withReplacedIrs(
                        buildFile.irs
                            .filterNot { it.safeAs<RepositoryIR>()?.repository in commonRepositories }
                            .toPersistentList()
                    ).let {
                        if (it.isRoot && commonRepositories.isNotEmpty()) {
                            val repositories = commonRepositories.map(::RepositoryIR).distinctAndSorted()
                            it.withIrs(AllProjectsRepositoriesIR(repositories))
                        } else it
                    }.asSuccess()
                }
            }
        }

        val createSettingsFileTask by pipelineTask(GenerationPhase.PROJECT_GENERATION) {
            runAfter(KotlinPlugin.createModules)
            runAfter(KotlinPlugin.createPluginRepositories)
            isAvailable = isGradle
            withAction {
                val (createBuildFile, buildFileName) = settingsGradleBuildFileData ?: return@withAction UNIT_SUCCESS

                val repositories = getPluginRepositoriesWithDefaultOnes().map { PluginManagementRepositoryIR(RepositoryIR(it)) }

                val plugins = mutableListOf<BuildSystemPluginIR>()

                val currentGradleVersion = GradleVersion.version(gradleVersion.settingValue.text)
                val foojayCanBeAdded = isFoojayPluginSupported(currentGradleVersion)

                if (foojayCanBeAdded) { // Check if foojay needs to be added
                    var foojayNeedsToBeAdded = false
                    val buildFiles = buildFiles.propertyValue

                    val platformTypes = buildFiles.flatMap { it.irs }
                        .filterIsInstance<KotlinBuildSystemPluginIR>()
                        .map { it.type }.toSet()

                    if (platformTypes.contains(KotlinBuildSystemPluginIR.Type.jvm)) {
                        foojayNeedsToBeAdded = true
                    } else if (platformTypes.contains(KotlinBuildSystemPluginIR.Type.multiplatform)) {
                        foojayNeedsToBeAdded = buildFiles.flatMap { it.modules.modules }
                            .flatMap { it.sourcesets }
                            .any { it is MultiplatformSourcesetIR && it.targetName == "jvm" }
                    }

                    if (foojayNeedsToBeAdded) {
                        val gradleToPluginsCompatibilityStore = GradleToPluginsCompatibilityStore.getInstance()
                        val foojayVersionString = gradleToPluginsCompatibilityStore.getFoojayVersion(currentGradleVersion)
                        if (foojayVersionString != null) {
                            val foojayVersion = Version.fromString(foojayVersionString)
                            plugins.add(FoojayPluginIR(foojayVersion))
                        } else {
                            LOG.error("Unable to get Foojay version for Gradle $currentGradleVersion")
                        }
                    }
                }

                val settingsGradleIR = SettingsGradleFileIR(
                    StructurePlugin.name.settingValue,
                    allModulesPaths.map { path -> path.joinToString(separator = "") { ":$it" } },
                    buildPersistenceList {
                        +repositories
                        +settingsGradleFileIRs.propertyValue
                    },
                    plugins
                )
                val buildFileText = createBuildFile().printBuildFile { settingsGradleIR.render(this) }
                service<FileSystemWizardService>().createFile(
                    projectPath / buildFileName,
                    buildFileText
                )
            }
        }
    }

    override val settings: List<PluginSetting<*, *>> = super.settings +
            listOf(
                gradleVersion,
                gradleHome
            )

    override val pipelineTasks: List<PipelineTask> = super.pipelineTasks +
            listOf(
                createGradlePropertiesFile,
                createLocalPropertiesFile,
                initGradleWrapperTask,
                createSettingsFileTask,
                mergeCommonRepositories,
            )
    override val properties: List<Property<*>> = super.properties +
            listOf(
                gradleProperties,
                settingsGradleFileIRs,
                localProperties
            )
}

val Reader.settingsGradleBuildFileData
    get() = when (buildSystemType) {
        BuildSystemType.GradleKotlinDsl ->
            BuildFileData(
                { GradlePrinter(GradlePrinter.GradleDsl.KOTLIN) },
                "settings.gradle.kts"
            )

        BuildSystemType.GradleGroovyDsl ->
            BuildFileData(
                { GradlePrinter(GradlePrinter.GradleDsl.GROOVY) },
                "settings.gradle"
            )

        else -> null
    }