// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin

import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.core.entity.*
import org.jetbrains.kotlin.tools.projectWizard.core.entity.properties.Property
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.PluginSetting
import org.jetbrains.kotlin.tools.projectWizard.core.service.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildFileIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.RepositoryIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.withIrs
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.ModuleConfigurator
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.StructurePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.buildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.pomIR
import org.jetbrains.kotlin.tools.projectWizard.plugins.projectPath
import org.jetbrains.kotlin.tools.projectWizard.settings.DisplayableSettingItem
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.*
import java.nio.file.Path

class KotlinPlugin(context: Context) : Plugin(context) {
    override val path = pluginPath

    companion object : PluginSettingsOwner() {
        override val pluginPath = "kotlin"

        private val moduleDependenciesValidator = settingValidator<List<Module>> { modules ->
            val allModules = modules.withAllSubModules(includeSourcesets = true).toSet()
            val allModulePaths = allModules.map(Module::path).toSet()
            allModules.flatMap { module ->
                module.dependencies.map { dependency ->
                    val isValidModule = when (dependency) {
                        is ModuleReference.ByPath -> dependency.path in allModulePaths
                        is ModuleReference.ByModule -> dependency.module in allModules
                    }
                    ValidationResult.create(isValidModule) {
                        KotlinNewProjectWizardBundle.message(
                            "plugin.kotlin.setting.modules.error.duplicated.modules",
                            dependency,
                            module.path
                        )
                    }
                }
            }.fold()
        }

        val version by property(
            // todo do not hardcode kind & repository
            WizardKotlinVersion(
                Versions.KOTLIN,
                KotlinVersionKind.M,
                Repositories.KOTLIN_EAP_MAVEN_CENTRAL,
                KotlinVersionProviderService.getBuildSystemPluginRepository(
                    KotlinVersionKind.M,
                    devRepositories = listOf(Repositories.JETBRAINS_KOTLIN_DEV)
                )
            )
        )

        val initKotlinVersions by pipelineTask(GenerationPhase.PREPARE_GENERATION) {
            title = KotlinNewProjectWizardBundle.message("plugin.kotlin.downloading.kotlin.versions")

            withAction {
                val version = service<KotlinVersionProviderService>().getKotlinVersion(projectKind.settingValue)
                KotlinPlugin.version.update { version.asSuccess() }
            }
        }

        val projectKind by enumSetting<ProjectKind>(
            KotlinNewProjectWizardBundle.message("plugin.kotlin.setting.project.kind"),
            GenerationPhase.FIRST_STEP,
        )

        private fun List<Module>.findDuplicatesByName() =
            groupingBy { it.name }.eachCount().filter { it.value > 1 }

        val modules by listSetting(
            KotlinNewProjectWizardBundle.message("plugin.kotlin.setting.modules"),
            GenerationPhase.SECOND_STEP,
            Module.parser,
        ) {
            validate { value ->
                val allModules = value.withAllSubModules()
                val duplicatedModules = allModules.findDuplicatesByName()
                if (duplicatedModules.isEmpty()) ValidationResult.OK
                else ValidationResult.ValidationError(
                    KotlinNewProjectWizardBundle.message(
                        "plugin.kotlin.setting.modules.error.duplicated.modules",
                        duplicatedModules.values.first(),
                        duplicatedModules.keys.first()
                    )
                )
            }

            validate { value ->
                value.withAllSubModules().filter { it.kind == ModuleKind.multiplatform }.map { module ->
                    val duplicatedModules = module.subModules.findDuplicatesByName()
                    if (duplicatedModules.isEmpty()) ValidationResult.OK
                    else ValidationResult.ValidationError(
                        KotlinNewProjectWizardBundle.message(
                            "plugin.kotlin.setting.modules.error.duplicated.targets",
                            duplicatedModules.values.first(),
                            duplicatedModules.keys.first()
                        )
                    )
                }.fold()
            }

            validate(moduleDependenciesValidator)
        }

        val createModules by pipelineTask(GenerationPhase.PROJECT_GENERATION) {
            runBefore(BuildSystemPlugin.createModules)
            runAfter(StructurePlugin.createProjectDir)
            withAction {
                BuildSystemPlugin.buildFiles.update {
                    val modules = modules.settingValue
                    val (buildFiles) = createBuildFiles(modules)
                    buildFiles.map { it.withIrs(RepositoryIR(DefaultRepository.MAVEN_CENTRAL)) }.asSuccess()
                }
            }
        }

        val createPluginRepositories by pipelineTask(GenerationPhase.PROJECT_GENERATION) {
            runBefore(BuildSystemPlugin.createModules)
            withAction {
                val version = version.propertyValue
                if (version.kind.isStable) return@withAction UNIT_SUCCESS
                val pluginRepository = version.buildSystemPluginRepository(buildSystemType)
                BuildSystemPlugin.pluginRepositoreis.addValues(pluginRepository) andThen
                        updateBuildFiles { buildFile ->
                            buildFile.withIrs(
                                version.repositories
                                    .map { RepositoryIR(it) }
                            ).asSuccess()
                        }
            }
        }

        val createResourceDirectories by booleanSetting("Generate Resource Folders", GenerationPhase.PROJECT_GENERATION) {
            defaultValue = value(true)
        }

        val createSourcesetDirectories by pipelineTask(GenerationPhase.PROJECT_GENERATION) {
            runAfter(createModules)
            withAction {
                fun Path?.createKotlinAndResourceDirectories(moduleConfigurator: ModuleConfigurator): TaskResult<Unit> {
                    if (this == null) return UNIT_SUCCESS
                    return with(service<FileSystemWizardService>()) {
                        createDirectory(this@createKotlinAndResourceDirectories / moduleConfigurator.kotlinDirectoryName) andThen
                                if (createResourceDirectories.settingValue) {
                                    createDirectory(this@createKotlinAndResourceDirectories / moduleConfigurator.resourcesDirectoryName)
                                } else {
                                    UNIT_SUCCESS
                                }
                    }
                }

                forEachModule { moduleIR ->
                    moduleIR.sourcesets.mapSequenceIgnore { sourcesetIR ->
                        sourcesetIR.path.createKotlinAndResourceDirectories(moduleIR.originalModule.configurator)
                    }
                }
            }
        }

        private fun Writer.createBuildFiles(modules: List<Module>): TaskResult<List<BuildFileIR>> =
            with(
                ModulesToIRsConverter(
                    ModulesToIrConversionData(
                        modules,
                        projectPath,
                        StructurePlugin.name.settingValue,
                        version.propertyValue,
                        buildSystemType,
                        pomIR()
                    )
                )
            ) { createBuildFiles() }
    }

    override val settings: List<PluginSetting<*, *>> =
        listOf(
            projectKind,
            modules,
            createResourceDirectories,
        )

    override val pipelineTasks: List<PipelineTask> =
        listOf(
            initKotlinVersions,
            createModules,
            createPluginRepositories,
            createSourcesetDirectories
        )
    override val properties: List<Property<*>> =
        listOf(version)

}

enum class ProjectKind(
    @Nls override val text: String,
    val supportedBuildSystems: Set<BuildSystemType>,
    val shortName: String = text,
    val message: String? = null,
) : DisplayableSettingItem {
    Singleplatform(
        KotlinNewProjectWizardBundle.message("project.kind.singleplatform"),
        supportedBuildSystems = BuildSystemType.values().toSet()
    ),
    Multiplatform(KotlinNewProjectWizardBundle.message("project.kind.multiplatform"), supportedBuildSystems = BuildSystemType.ALL_GRADLE),
    Android(KotlinNewProjectWizardBundle.message("project.kind.android"), supportedBuildSystems = BuildSystemType.ALL_GRADLE),
    Js(KotlinNewProjectWizardBundle.message("project.kind.kotlin.js"), supportedBuildSystems = BuildSystemType.ALL_GRADLE),
}

fun List<Module>.withAllSubModules(includeSourcesets: Boolean = false): List<Module> = buildList {
    fun handleModule(module: Module) {
        +module
        if (module.kind != ModuleKind.multiplatform
            || includeSourcesets && module.kind == ModuleKind.multiplatform
        ) {
            module.subModules.forEach(::handleModule)
        }
    }
    forEach(::handleModule)
}
