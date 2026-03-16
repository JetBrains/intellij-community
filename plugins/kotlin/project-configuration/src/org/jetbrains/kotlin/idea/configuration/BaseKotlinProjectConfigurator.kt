// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndEdtWriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.psi.search.FileTypeIndex
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootMap
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.framework.ui.ConfigureDialogWithModulesAndVersion
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.idea.statistics.KotlinProjectConfigurationError
import org.jetbrains.kotlin.idea.statistics.KotlinProjectSetupFUSCollector
import org.jetbrains.kotlin.idea.util.application.executeCommand
import java.nio.file.Path
import kotlin.io.path.relativeTo

@ApiStatus.Experimental
abstract class BaseKotlinProjectConfigurator : KotlinProjectConfigurator {

    override fun getStatus(moduleSourceRootGroup: ModuleSourceRootGroup): ConfigureKotlinStatus {
        val module = moduleSourceRootGroup.baseModule
        if (!isApplicable(module)) {
            return ConfigureKotlinStatus.NON_APPLICABLE
        }

        if (moduleSourceRootGroup.sourceRootModules.all(Module::hasKotlinPluginEnabled)) {
            return ConfigureKotlinStatus.CONFIGURED
        }

        return ConfigureKotlinStatus.CAN_BE_CONFIGURED
    }

    override fun canRunAutoConfig(): Boolean = isAutoConfigurationEnabled()

    override suspend fun calculateAutoConfigSettings(module: Module): AutoConfigurationSettings? =
        readAction {
            calculateAutoConfigSettingsReadAction(module)
        }

    @RequiresReadLock
    protected abstract fun calculateAutoConfigSettingsReadAction(module: Module): AutoConfigurationSettings?

    override fun queueSyncIfNeeded(project: Project) {
        KotlinProjectConfigurationService.getInstance(project).queueSync()
    }

    override suspend fun runAutoConfig(settings: AutoConfigurationSettings) {
        val module = settings.module
        val project = module.project
        val moduleVersions = readAction {
            getKotlinVersionsAndModules(project, this).first
        }
        val jvmTargets = readAction {
            checkModuleJvmTargetCompatibility(listOf(module), settings.kotlinVersion).moduleJvmTargets
        }
        val collector = NotificationMessageCollector.create(project)
        val notificationHolder = notificationHolder(project)
        logStartConfigureKotlin(project, settings.kotlinVersion.rawVersion)
        val commandKey = "command.name.configure.kotlin.automatically"
        val resultBuilder = withModalProgress(project, KotlinProjectConfigurationBundle.message(commandKey)) {
            doInternalConfigureUnderProgress(
                project,
                modules = listOf(module),
                kotlinVersionsAndModules = moduleVersions,
                version = settings.kotlinVersion,
                collector = collector,
                notificationHolder = notificationHolder,
                modulesAndJvmTargets = jvmTargets,
                commandKey = commandKey,
                isAutoConfig = true
            )
        }
        val result = resultBuilder.build()
        val error = result.error
        if (error == null) {
            queueSyncIfNeeded(project)

            notificationHolder
                .showAutoConfiguredNotification(module.name, result.changedFiles.calculateChanges())

            collector.showNotification()
            ConfigureKotlinNotificationManager.expireOldNotifications(project)
        } else {
            KotlinProjectSetupFUSCollector.logConfigureKtFailed(project, error)
        }
    }

    override fun configure(project: Project, excludeModules: Collection<Module>) {
        configureAndGetConfiguredModules(project, excludeModules)
    }

    @RequiresEdt
    override fun configureAndGetConfiguredModules(project: Project, excludeModules: Collection<Module>): Set<Module> {
        val dialog = ConfigureDialogWithModulesAndVersion(project, this, excludeModules, getMinimumSupportedVersion())

        dialog.show()
        if (!dialog.isOK) return emptySet()

        val kotlinVersion = dialog.kotlinVersion ?: return emptySet()
        val modules = dialog.modulesToConfigure.takeIf { it.isNotEmpty() } ?: return emptySet()
        val kotlinVersionsAndModules = dialog.versionsAndModules
        val modulesAndJvmTargets = dialog.modulesAndJvmTargets

        logStartConfigureKotlin(project, kotlinVersion)

        val collector: NotificationMessageCollector = NotificationMessageCollector.create(project)
        val notificationHolder = notificationHolder(project)

        val configurationResult =
            doInternalConfigure(
                project,
                IdeKotlinVersion.get(kotlinVersion),
                modules,
                collector,
                notificationHolder,
                kotlinVersionsAndModules,
                modulesAndJvmTargets
            ).build()

        val configuredModules = configurationResult.configuredModules
        val error = (configurationResult.error
            ?: KotlinProjectConfigurationError.OTHER.takeIf { configuredModules.isEmpty() })
        error?.let { logConfigureKotlinFailed(project, it) }

        val projectPath = project.basePath?.let { Path.of(it) }

        queueSyncIfNeeded(project)

        for (file in configurationResult.changedFiles.getChangedFiles()) {
            val virtualFile = file.virtualFile ?: continue
            val nioPath = virtualFile.toNioPath()
            val path =
                projectPath?.let { path -> nioPath.relativeTo(path).normalize().toString() }
                    ?: nioPath.toString()
            collector.addMessage(KotlinProjectConfigurationBundle.message("text.was.modified", path))
            OpenFileAction.openFile(virtualFile, project)
        }

        collector.showNotification()
        notificationHolder.onManualConfigurationCompleted()
        ConfigureKotlinNotificationManager.expireOldNotifications(project)

        return configuredModules
    }

    protected open fun logStartConfigureKotlin(project: Project, kotlinVersion: String) {
        KotlinProjectSetupFUSCollector.logChosenKotlinVersion(project, kotlinVersion)

        KotlinProjectSetupFUSCollector.logStartConfigureKt(project)
    }

    protected open fun logConfigureKotlinFailed(project: Project, error: KotlinProjectConfigurationError) {
        KotlinProjectSetupFUSCollector.logConfigureKtFailed(project, error)
    }

    protected open fun getMinimumSupportedVersion(): String = "2.0.0"

    abstract fun notificationHolder(project: Project): KotlinAutoConfigurationNotificationHolder

    protected open fun doInternalConfigure(
        project: Project,
        kotlinVersion: IdeKotlinVersion,
        modules: List<Module>,
        collector: NotificationMessageCollector,
        notificationHolder: KotlinAutoConfigurationNotificationHolder,
        kotlinVersionsAndModules: Map<String, Map<String, Module>>,
        modulesAndJvmTargets: Map<ModuleName, TargetJvm>
    ): ConfigurationResultBuilder {
        val commandKey = "command.name.configure.kotlin"
        return runWithModalProgressBlocking(project, KotlinProjectConfigurationBundle.message(commandKey)) {
            doInternalConfigureUnderProgress(
                project,
                modules,
                kotlinVersionsAndModules,
                kotlinVersion,
                collector,
                notificationHolder,
                modulesAndJvmTargets,
                commandKey
            )
        }
    }

    protected open fun filterApplicableModules(modules: List<Module>): List<Module> =
        modules

    protected suspend fun doInternalConfigureUnderProgress(
        project: Project,
        modules: List<Module>,
        kotlinVersionsAndModules: Map<String, Map<String, Module>>,
        version: IdeKotlinVersion,
        collector: NotificationMessageCollector,
        notificationHolder: KotlinAutoConfigurationNotificationHolder,
        modulesAndJvmTargets: Map<ModuleName, TargetJvm>,
        commandKey: String,
        isAutoConfig: Boolean = false
    ): ConfigurationResultBuilder {
        return reportSequentialProgress { reporter ->
            val applicableModules = filterApplicableModules(modules)
            reporter.nextStep(
                endFraction = 30,
                KotlinProjectConfigurationBundle.message("step.configure.kotlin.preparing")
            )
            readAndEdtWriteAction {
                // First, check all the files and abort if something would not work
                val configureAction =
                    createConfigureAction(
                        project,
                        applicableModules,
                        version,
                        collector,
                        kotlinVersionsAndModules,
                        modulesAndJvmTargets
                    )

                // Now that everything has been read and verified, apply the changes
                writeAction {
                    reporter.nextStep(
                        endFraction = 100,
                        KotlinProjectConfigurationBundle.message("step.configure.kotlin.writing")
                    )

                    project.executeCommand(KotlinProjectConfigurationBundle.message(commandKey)) {
                        val resultBuilder = configureAction()
                        val configurationResult = resultBuilder.build()
                        if (configurationResult.error == null) {
                            // attempt to configure compiler plugin during the same step as kotlin configuration
                            // when module dependency is known
                            configurationResult.configuredModules.forEach { module ->
                                configureCompilerPluginsForModule(module, resultBuilder)
                            }
                        }
                        addUndoConfigurationListener(
                            project,
                            resultBuilder.build().configuredModules,
                            isAutoConfig,
                            notificationHolder
                        )
                        resultBuilder
                    }
                }
            }
        }
    }

    protected abstract fun createConfigureAction(
        project: Project,
        modulesToConfigure: List<Module>,
        kotlinVersion: IdeKotlinVersion,
        collector: NotificationMessageCollector,
        kotlinVersionsAndModules: Map<String, Map<String, Module>>,
        modulesAndJvmTargets: Map<ModuleName, TargetJvm> = emptyMap()
    ): () -> ConfigurationResultBuilder

    private fun configurableModulesWithKotlinFiles(project: Project): List<ModuleSourceRootGroup> {
        val projectScope = project.projectScope()
        val projectFileIndex = ProjectFileIndex.getInstance(project)
        val kotlinFiles = runReadAction { FileTypeIndex.getFiles(KotlinFileType.INSTANCE, projectScope) }
        val modules = kotlinFiles.mapNotNullTo(mutableSetOf()) { ktFile: VirtualFile ->
            runReadAction {
                if (projectFileIndex.isInSourceContent(ktFile)) {
                    projectFileIndex.getModuleForFile(ktFile)
                } else null
            }
        }
        val groupByBaseModules = ModuleSourceRootMap(project).groupByBaseModules(modules)
        return groupByBaseModules
    }

    private fun effectiveModules(project: Project, modules: Collection<Module>): Collection<Module>? {
        val rootModule = runReadAction { getRootModule(project) } ?: return null
        return if (modules.contains(rootModule)) {
            val moduleSourceRootGroups = configurableModulesWithKotlinFiles(project)
            val rootGroup = moduleSourceRootGroups.firstOrNull { it.baseModule == rootModule }
            modules.filter { it != rootModule } + (rootGroup?.sourceRootModules ?: emptyList())
        } else {
            modules
        }
    }

    private fun Collection<Module>.configuratorsByModule(): List<Pair<Module, List<KotlinProjectPostConfigurator>>>? {
        val project = this.firstOrNull()?.project ?: return null
        val effectiveModules = effectiveModules(project, this) ?: return null
        val configuratorsByModule = effectiveModules.mapNotNull { module ->
            val configuratorsByModules =
                KotlinProjectPostConfigurator.EP_NAME.extensionList
                    .filter {
                        runReadAction {
                            it.isApplicable(module)
                        }
                    }
            if (configuratorsByModules.isEmpty()) return@mapNotNull null
            module to configuratorsByModules
        }

        return configuratorsByModule.takeIf { it.isNotEmpty() }
    }

    protected fun configureCompilerPluginsForModule(module: Module, resultBuilder: ConfigurationResultBuilder) {
        val configuratorsByModule = listOf(module).configuratorsByModule() ?: return

        configuratorsByModule.forEach { (module, configuratorsByModules) ->
            configuratorsByModules.forEach { it.configureModule(module, resultBuilder) }
        }
    }

}
