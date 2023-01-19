// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList


import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.core.service.WizardKotlinVersion
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.*
import org.jetbrains.kotlin.tools.projectWizard.plugins.StructurePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.buildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.gradle.GradlePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.isGradle
import org.jetbrains.kotlin.tools.projectWizard.plugins.templates.TemplatesPlugin
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.*
import java.nio.file.Path
import java.util.*

data class ModulesToIrConversionData(
    val rootModules: List<Module>,
    val projectPath: Path,
    val projectName: String,
    val kotlinVersion: WizardKotlinVersion,
    val buildSystemType: BuildSystemType,
    val pomIr: PomIR
) {
    val allModules = rootModules.withAllSubModules()
    val isSingleRootModuleMode = rootModules.size == 1

    val moduleByPath = rootModules.withAllSubModules(includeSourcesets = true).associateBy(Module::path)

    fun getDependentModules(from: Module): TaskResult<List<Module>> =
        from.dependencies.mapSequence { to ->
            moduleByPath[to.path].toResult { InvalidModuleDependencyError(from.name, to.path.toString()) }
        }
}

data class ModulesToIrsState(
    val parentPath: Path,
    val parentModuleHasTransitivelySpecifiedKotlinVersion: Boolean
)

private fun ModulesToIrsState.stateForSubModule(currentModulePath: Path) =
    copy(
        parentPath = currentModulePath,
        parentModuleHasTransitivelySpecifiedKotlinVersion = true
    )

class ModulesToIRsConverter(
    val data: ModulesToIrConversionData
) {

    // TODO get rid of mutable state
    private val rootBuildFileIrs = mutableListOf<BuildSystemIR>()

    // check if we need to flatten our module structure to a single-module
    // as we always have a root module in the project
    // which is redundant for a single module projects
    private val needFlattening: Boolean
        get() {
            if ( // We want to have root build file for android or ios projects
                data.allModules.any { it.configurator.requiresRootBuildFile }
            ) return false
            return data.isSingleRootModuleMode
        }

    private val irsToAddToModules = hashMapOf<Module, MutableList<BuildSystemIR>>()
    private val moduleToBuildFile = hashMapOf<Module, BuildFileIR>()

    private fun calculatePathForModule(module: Module, rootPath: Path) = when {
        needFlattening && module.isRootModule -> data.projectPath
        else -> rootPath / module.name
    }

    fun Writer.createBuildFiles(): TaskResult<List<BuildFileIR>> = with(data) {
        val needExplicitRootBuildFile = !needFlattening
        val initialState = ModulesToIrsState(projectPath, parentModuleHasTransitivelySpecifiedKotlinVersion = false)

        val parentModuleHasKotlinVersion = allModules.any { module ->
            module.configurator is AndroidSinglePlatformModuleConfiguratorBase ||
            module.configurator is MppModuleConfigurator && module.subModules.any { subModule ->
                        subModule.configurator is AndroidTargetConfiguratorBase &&
                        subModule.dependencies.filterIsInstance<ModuleReference.ByModule>().any { moduleRef ->
                            moduleRef.module.configurator == MppModuleConfigurator
                        }
            }
        }

        allModules.mapSequenceIgnore { module ->
            forModuleEachDependency(module) { from, to, dependencyType ->
                with(dependencyType) {
                    runArbitraryTaskBeforeIRsCreated(from, to)
                }
            }
        } andThen rootModules.mapSequence { module ->
            createBuildFileForModule(
                module,
                initialState.copy(parentModuleHasTransitivelySpecifiedKotlinVersion = parentModuleHasKotlinVersion)
            )
        }.map { it.flatten() }.map { buildFiles ->
            if (needExplicitRootBuildFile) buildFiles + createRootBuildFile()
            else buildFiles
        }.map { buildFiles ->
            buildFiles.map { buildFile ->
                val irs = buildFile.fromModules.flatMap { irsToAddToModules[it]?.toList() ?: emptyList() }
                buildFile.withIrs(irs)
            }
        }
    }

    private fun Reader.createRootBuildFile(): BuildFileIR = with(data) {
        BuildFileIR(
            projectName,
            projectPath,
            RootFileModuleStructureIR(persistentListOf()),
            emptyList(),
            pomIr,
            isRoot = true,
            renderPomIr = StructurePlugin.renderPomIR.settingValue,
            rootBuildFileIrs.toPersistentList()
        )
    }


    private fun Writer.createBuildFileForModule(
        module: Module,
        state: ModulesToIrsState
    ): TaskResult<List<BuildFileIR>> = when (val configurator = module.configurator) {
        is MppModuleConfigurator -> createMultiplatformModule(module, state)
        is CustomPlatformModuleConfigurator -> configurator.createPlatformModule(this, this@ModulesToIRsConverter, module, state)
        is SinglePlatformModuleConfigurator -> createSinglePlatformModule(this, module, configurator, state, StructurePlugin.renderPomIR.settingValue)
        else -> Success(emptyList())
    }

    private fun <T : Any> forModuleEachDependency(
        from: Module,
        action: suspend ComputeContext<NoState>.(from: Module, to: Module, dependencyType: ModuleDependencyType) -> TaskResult<T>
    ): TaskResult<List<T>> {
        return from.dependencies.mapComputeM { dependency ->
            val to = data.moduleByPath.getValue(dependency.path)
            val (dependencyType) = ModuleDependencyType.getPossibleDependencyType(from, to)
                .toResult { InvalidModuleDependencyError(from, to) }
            action(from, to, dependencyType)
        }.sequence()
    }

    fun createSinglePlatformModule(
        writer: Writer,
        module: Module,
        configurator: SinglePlatformModuleConfigurator,
        state: ModulesToIrsState,
        renderPomIr: Boolean
    ): TaskResult<List<BuildFileIR>> = computeM {
        val modulePath = calculatePathForModule(module, state.parentPath)
        val (moduleDependencies) = writer.createModuleDependencies(module)
        writer.mutateProjectStructureByModuleConfigurator(module, modulePath)
        val buildFileIR = run {
            if (!configurator.needCreateBuildFile) return@run null
            val dependenciesIRs = buildPersistenceList<BuildSystemIR> {
                +moduleDependencies
                with(configurator) { +createModuleIRs(writer, data, module) }
                addIfNotNull(writer.addSdtLibForNonGradleSignleplatformModule(module))
            }

            val moduleIr = SingleplatformModuleIR(
                if (modulePath == data.projectPath) data.projectName else module.name,
                modulePath,
                dependenciesIRs,
                module.template,
                module,
                module.sourceSets.map { sourceset ->
                    val path = if (sourceset.createDirectory) modulePath / Defaults.SRC_DIR / sourceset.sourcesetType.name else null
                    SingleplatformSourcesetIR(
                        sourceset.sourcesetType,
                        path,
                        persistentListOf(),
                        sourceset
                    )
                }
            )
            BuildFileIR(
                module.name,
                modulePath,
                SingleplatformModulesStructureWithSingleModuleIR(
                    moduleIr,
                    persistentListOf()
                ),
                listOf(module),
                data.pomIr.copy(),
                isRoot = false, /* TODO */
                renderPomIr = renderPomIr,
                writer.createBuildFileIRs(module, state)
            ).also {
                moduleToBuildFile[module] = it
            }
        }

        module.subModules.mapSequence { subModule ->
            writer.createBuildFileForModule(
                subModule,
                state.stateForSubModule(modulePath)
            )
        }.map { it.flatten() }
            .map { children ->
                buildFileIR?.let { children + it } ?: children
            }
    }

    private fun Writer.createMultiplatformModule(
        module: Module,
        state: ModulesToIrsState
    ): TaskResult<List<BuildFileIR>> = compute {
        with(data) {
            val modulePath = calculatePathForModule(module, state.parentPath)
            mutateProjectStructureByModuleConfigurator(module, modulePath)
            val targetIrs = module.subModules.flatMap { subModule ->
                with(subModule.configurator as TargetConfigurator) { createTargetIrs(subModule) }
            }

            val (targetModuleIrs) = module.subModules.mapSequence { target ->
                createTargetModule(target, modulePath)
            }

            BuildFileIR(
                projectName,
                modulePath,
                MultiplatformModulesStructureIR(
                    targetIrs,
                    FakeMultiplatformModuleIR(
                        module.name,
                        modulePath,
                        module.template,
                        targetModuleIrs,
                        module,
                    ),
                    targetModuleIrs,
                    persistentListOf()
                ),
                module.subModules + module,
                pomIr,
                isRoot = false,
                renderPomIr = StructurePlugin.renderPomIR.settingValue,
                buildPersistenceList {
                    +createBuildFileIRs(module, state)
                    module.subModules.forEach { +createBuildFileIRs(it, state) }
                }
            ).also { buildFile ->
                moduleToBuildFile[module] = buildFile
                module.subModules.forEach { subModule ->
                    moduleToBuildFile[subModule] = buildFile
                }
            }.asSingletonList()
        }
    }

    private fun Writer.createModuleDependencies(module: Module): TaskResult<List<BuildSystemIR>> =
        forModuleEachDependency(module) { from, to, dependencyType ->
            with(dependencyType) {
                @Suppress("DEPRECATION")
                unsafeSettingWriter {
                    runArbitraryTask(
                        this@createModuleDependencies,
                        module,
                        to,
                        to.path.considerSingleRootModuleMode(data.isSingleRootModuleMode).asPath(),
                        data
                    ).ensure()
                }
                irsToAddToModules.getOrPut(to) { mutableListOf() } += createToIRs(module, to, data).get()
                createDependencyIrs(module, to, data).asSuccess()
            }
        }.map { it.flatten() }

    private fun Writer.createTargetModule(target: Module, modulePath: Path): TaskResult<MultiplatformModuleIR> = compute {
        val (moduleDependencies) = createModuleDependencies(target)
        mutateProjectStructureByModuleConfigurator(target, modulePath)
        val sourcesetss = target.sourceSets.map { sourceset ->
            val sourcesetName = target.name + sourceset.sourcesetType.name.capitalize(Locale.US)
            val path = if (sourceset.createDirectory) modulePath / Defaults.SRC_DIR / sourcesetName else null
            MultiplatformSourcesetIR(
                sourceset.sourcesetType,
                path,
                target.name,
                persistentListOf(),
                sourceset
            )
        }
        MultiplatformModuleIR(
            target.name,
            modulePath,
            buildPersistenceList {
                +moduleDependencies
                with(target.configurator) { +createModuleIRs(this@createTargetModule, data, target) }
            },
            target.template,
            target,
            sourcesetss
        )
    }

    private fun Writer.mutateProjectStructureByModuleConfigurator(
        module: Module,
        modulePath: Path
    ): TaskResult<Unit> = with(module.configurator) {
        compute {
            rootBuildFileIrs += createRootBuildFileIrs(data)
            module.template?.let { template ->
                rootBuildFileIrs += with(template) { createRootBuildFileIrs() }
            }
            runArbitraryTask(data, module, modulePath).ensure()
            TemplatesPlugin.addFileTemplates.execute(createTemplates(data, module, modulePath)).ensure()
            if (this@with is GradleModuleConfigurator) {
                GradlePlugin.settingsGradleFileIRs.addValues(
                    createSettingsGradleIRs(this@mutateProjectStructureByModuleConfigurator, module, data)
                ).ensure()
            }
        }
    }

    private fun Reader.addSdtLibForNonGradleSignleplatformModule(module: Module): KotlinStdlibDependencyIR? {
        // for gradle stdlib is added by default KT-38221
        if (buildSystemType.isGradle) return null
        val stdlibType = module.configurator.createStdlibType(data, module) ?: return null
        return KotlinStdlibDependencyIR(
            type = stdlibType,
            isInMppModule = false,
            kotlinVersion = data.kotlinVersion,
            dependencyType = DependencyType.MAIN
        )
    }

    private fun Reader.createBuildFileIRs(
        module: Module,
        state: ModulesToIrsState
    ) = buildPersistenceList<BuildSystemIR> {
        val kotlinPlugin = module.configurator.createKotlinPluginIR(data, module)
            ?.let { plugin ->
                // do not print version for non-root modules for gradle
                val needRemoveVersion = data.buildSystemType.isGradle
                        && state.parentModuleHasTransitivelySpecifiedKotlinVersion
                when {
                    needRemoveVersion -> plugin.copy(version = null)
                    else -> plugin
                }
            }
        addIfNotNull(kotlinPlugin)
        +with(module.configurator) { createBuildFileIRs(this@createBuildFileIRs, data, module) }
            .let {
                module.template?.run { updateBuildFileIRs(it) } ?: it
            }
    }
}