// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven.configuration

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix
import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.JavaProjectModelModificationService
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.WritingAccessProvider
import com.intellij.platform.backend.observation.Observation
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenArtifactScope
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.plugin.KotlinCompilerVersionProvider
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.base.projectStructure.toModuleGroup
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.*
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersion
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersionOrDefault
import org.jetbrains.kotlin.idea.framework.ui.ConfigureDialogWithModulesAndVersion
import org.jetbrains.kotlin.idea.maven.*
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.idea.projectConfiguration.LibraryJarDescriptor
import org.jetbrains.kotlin.idea.quickfix.AbstractChangeFeatureSupportLevelFix
import org.jetbrains.kotlin.idea.statistics.KotlinJ2KOnboardingConfigurationError
import org.jetbrains.kotlin.idea.statistics.KotlinJ2KOnboardingFUSCollector
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand

abstract class KotlinMavenConfigurator protected constructor(
    private val testArtifactId: String?,
    private val addJunit: Boolean,
    override val name: String,
    override val presentableText: String
) : KotlinProjectConfigurator {

    override fun canRunAutoConfig(): Boolean = isAutoConfigurationEnabled()

    override fun getStatus(moduleSourceRootGroup: ModuleSourceRootGroup): ConfigureKotlinStatus {
        val module = moduleSourceRootGroup.baseModule
        if (module.buildSystemType != BuildSystemType.Maven)
            return ConfigureKotlinStatus.NON_APPLICABLE

        val psi = runReadAction { findModulePomFile(module) }
        when {
            psi == null -> {
                return logErrorAndReturnBrokenStatus(
                    module.project,
                    KotlinJ2KOnboardingConfigurationError.NO_POM_FILE
                )
            }

            !psi.isValid -> {
                return logErrorAndReturnBrokenStatus(
                    module.project,
                    KotlinJ2KOnboardingConfigurationError.PSI_FOR_POM_IS_NOT_VALID
                )
            }

            psi !is XmlFile -> {
                return logErrorAndReturnBrokenStatus(
                    module.project,
                    KotlinJ2KOnboardingConfigurationError.POM_IS_NOT_XML
                )
            }

            psi.virtualFile == null -> {
                return logErrorAndReturnBrokenStatus(
                    module.project,
                    KotlinJ2KOnboardingConfigurationError.VIRTUAL_FILE_DOESNT_EXIST_FOR_PSI_FILE
                )
            }

            isKotlinModule(module) -> {
                return runReadAction { checkPluginConfiguration(module, psi) }
            }

            else -> return ConfigureKotlinStatus.CAN_BE_CONFIGURED
        }
    }

    private fun logErrorAndReturnBrokenStatus(project: Project, error: KotlinJ2KOnboardingConfigurationError): ConfigureKotlinStatus {
        KotlinJ2KOnboardingFUSCollector.logConfigureKtFailed(project, error)
        return ConfigureKotlinStatus.BROKEN
    }

    override fun isApplicable(module: Module): Boolean {
        return module.buildSystemType == BuildSystemType.Maven
    }

    protected open fun checkPluginConfiguration(module: Module, psi: XmlFile): ConfigureKotlinStatus {
        val pom = PomFile.forFileOrNull(psi) ?: return ConfigureKotlinStatus.NON_APPLICABLE

        if (hasKotlinPlugin(pom)) {
            return ConfigureKotlinStatus.CONFIGURED
        }

        val project = module.project
        val mavenProjectsManager = MavenProjectsManager.getInstance(project)
        val mavenProject = mavenProjectsManager.findProject(module) ?: return logErrorAndReturnBrokenStatus(
            project,
            KotlinJ2KOnboardingConfigurationError.MAVEN_PROJECT_FOR_MODULE_NOT_FOUND
        )

        val kotlinPluginId = kotlinPluginId()
        val kotlinPlugin = mavenProject.plugins.find { it.mavenId.equals(kotlinPluginId.groupId, kotlinPluginId.artifactId) }
            ?: return ConfigureKotlinStatus.CAN_BE_CONFIGURED

        if (kotlinPlugin.executions.any { it.goals.any(this::isRelevantGoal) }) {
            return ConfigureKotlinStatus.CONFIGURED
        }

        return ConfigureKotlinStatus.CAN_BE_CONFIGURED
    }

    protected fun hasKotlinPlugin(pom: PomFile): Boolean {
        val plugin = pom.findPlugin(kotlinPluginId()) ?: return false

        return plugin.executions.executions.any { execution ->
            execution.goals.goals.any { isRelevantGoal(it.stringValue ?: "") }
        }
    }

    override fun configure(project: Project, excludeModules: Collection<Module>) {
        configureAndGetConfiguredModules(project, excludeModules)
    }

    override fun configureAndGetConfiguredModules(project: Project, excludeModules: Collection<Module>): Set<Module> {
        val dialog = ConfigureDialogWithModulesAndVersion(project, this, excludeModules, getMinimumSupportedVersion())
        if (dialog.modulesToConfigure.isEmpty()) return emptySet()

        dialog.show()
        if (!dialog.isOK) return emptySet()
        val kotlinVersion = dialog.kotlinVersion ?: return emptySet()
        KotlinJ2KOnboardingFUSCollector.logChosenKotlinVersion(project, kotlinVersion)

        KotlinJ2KOnboardingFUSCollector.logStartConfigureKt(project)

        val configuredModules = mutableSetOf<Module>()
        project.executeWriteCommand(KotlinMavenBundle.message("configure.title")) {
            val collector = NotificationMessageCollector.create(project)
            for (module in excludeMavenChildrenModules(project, dialog.modulesToConfigure)) {
                val file = findModulePomFile(module)
                if (file != null) {
                    val configured = configureModule(module, file, IdeKotlinVersion.get(kotlinVersion), collector)
                    if (configured) {
                        queueSyncIfNeeded(project)
                        OpenFileAction.openFile(file.virtualFile, project)
                        configuredModules.add(module)
                    } else {
                        KotlinJ2KOnboardingFUSCollector.logConfigureKtFailed(project, KotlinJ2KOnboardingConfigurationError.OTHER)
                    }
                } else {
                    KotlinJ2KOnboardingFUSCollector.logConfigureKtFailed(
                        project,
                        KotlinJ2KOnboardingConfigurationError.BUILD_SCRIPT_FOR_MODULE_IS_ABSENT_OR_NOT_WRITABLE
                    )
                    showErrorMessage(project, KotlinMavenBundle.message("error.cant.find.pom.for.module", module.name))
                }
            }
            val notificationHolder = KotlinMavenAutoConfigurationNotificationHolder.getInstance(project)
            notificationHolder.onManualConfigurationCompleted()
            collector.showNotification()

            addUndoConfigurationListener(project, modules = null, isAutoConfig = false, notificationHolder)
            ConfigureKotlinNotificationManager.expireOldNotifications(project)
        }
        return configuredModules
    }

    override fun queueSyncIfNeeded(project: Project) {
        KotlinProjectConfigurationService.getInstance(project).queueSync()
    }

    override suspend fun queueSyncAndWaitForProjectToBeConfigured(project: Project) {
        queueSyncIfNeeded(project)
        Observation.awaitConfiguration(project)
    }

    override suspend fun calculateAutoConfigSettings(module: Module): AutoConfigurationSettings? {
        return readAction {
            calculateAutoConfigSettingsReadAction(module)
        }
    }

    private fun calculateAutoConfigSettingsReadAction(module: Module): AutoConfigurationSettings? {
        if (!isAutoConfigurationEnabled()) return null

        val moduleGroup = module.toModuleGroup()
        val status = getStatus(moduleGroup)
        if (status != ConfigureKotlinStatus.CAN_BE_CONFIGURED) return null

        val project = module.project
        if (project.isMavenSyncPending(module) || project.isMavenSyncInProgress()) return null

        if (module.hasKotlinPluginEnabled()) return null

        val compilerVersionFromSettings = KotlinCompilerVersionProvider.getVersion(module) ?: KotlinPluginLayout.standaloneCompilerVersion
        val baseModule = moduleGroup.baseModule
        return AutoConfigurationSettings(baseModule, compilerVersionFromSettings)
    }

    override fun isAutoConfigurationEnabled(): Boolean = Registry.`is`("kotlin.configuration.maven.autoConfig.enabled", true)

    override suspend fun runAutoConfig(settings: AutoConfigurationSettings) {
        val module = settings.module
        val project = module.project
        KotlinJ2KOnboardingFUSCollector.logStartConfigureKt(project, true)
        reportSequentialProgress { reporter ->
            reporter.nextStep(endFraction = 30, KotlinProjectConfigurationBundle.message("step.configure.kotlin.preparing"))
            edtWriteAction {
                @Suppress("DialogTitleCapitalization")
                project.executeWriteCommand(KotlinProjectConfigurationBundle.message("command.name.configure.kotlin.automatically")) {
                    val file = findModulePomFile(module)
                    reporter.nextStep(endFraction = 100, KotlinProjectConfigurationBundle.message("step.configure.kotlin.writing"))
                    if (file != null) {
                        val changedBuildFiles = ChangedConfiguratorFiles()
                        changedBuildFiles.storeOriginalFileContent(file)
                        val configured = configureModuleSilently(module, file, settings.kotlinVersion)
                        if (configured) {
                            queueSyncIfNeeded(project)
                            val notificationHolder = KotlinMavenAutoConfigurationNotificationHolder.getInstance(project)
                            addUndoConfigurationListener(project, listOf(module), isAutoConfig = true, notificationHolder)
                            notificationHolder
                                .showAutoConfiguredNotification(module.name, changedBuildFiles.calculateChanges())

                            val collector = NotificationMessageCollector.create(project)
                            collector.showNotification()
                            ConfigureKotlinNotificationManager.expireOldNotifications(project)
                        } else {
                            KotlinJ2KOnboardingFUSCollector.logConfigureKtFailed(project, KotlinJ2KOnboardingConfigurationError.OTHER)
                        }
                    } else {
                        KotlinJ2KOnboardingFUSCollector.logConfigureKtFailed(
                            project,
                            KotlinJ2KOnboardingConfigurationError.BUILD_SCRIPT_FOR_MODULE_IS_ABSENT_OR_NOT_WRITABLE
                        )
                        showErrorMessage(project, KotlinMavenBundle.message("error.cant.find.pom.for.module", module.name))
                    }
                }
            }
        }
    }

    protected open fun getMinimumSupportedVersion(): String = "1.0.0"

    protected abstract fun isKotlinModule(module: Module): Boolean
    protected abstract fun isRelevantGoal(goalName: String): Boolean

    protected abstract fun createExecutions(pomFile: PomFile, kotlinPlugin: MavenDomPlugin, module: Module)
    protected abstract fun getStdlibArtifactId(module: Module, version: IdeKotlinVersion): String

    open fun configureModule(module: Module, file: PsiFile, version: IdeKotlinVersion, collector: NotificationMessageCollector): Boolean =
        changePomFile(module, file, version, collector)

    private fun configureModuleSilently(module: Module, file: PsiFile, version: IdeKotlinVersion): Boolean =
        changePomFile(module, file, version, collector = null)

    private fun changePomFile(
        module: Module,
        file: PsiFile,
        version: IdeKotlinVersion,
        collector: NotificationMessageCollector?
    ): Boolean {
        val virtualFile = file.virtualFile
        val project = module.project
        if (virtualFile == null) {
            KotlinJ2KOnboardingFUSCollector.logConfigureKtFailed(
                project,
                KotlinJ2KOnboardingConfigurationError.VIRTUAL_FILE_DOESNT_EXIST_FOR_PSI_FILE
            )
            error("Virtual file should exists for psi file " + file.name)
        }
        val domModel = MavenDomUtil.getMavenDomProjectModel(project, virtualFile)
        if (domModel == null) {
            KotlinJ2KOnboardingFUSCollector.logConfigureKtFailed(
                project,
                KotlinJ2KOnboardingConfigurationError.DOM_MODEL_DOESNT_EXIST
            )
            showErrorMessage(project, null)
            return false
        }

        val pom = PomFile.forFileOrNull(file as XmlFile)
        if (pom == null) {
            KotlinJ2KOnboardingFUSCollector.logConfigureKtFailed(
                project,
                KotlinJ2KOnboardingConfigurationError.WASNT_ABLE_TO_TRANSFORM_XML_TO_POM
            )
            return false
        }
        pom.addProperty(KOTLIN_VERSION_PROPERTY, version.artifactVersion)

        pom.addDependency(
            MavenId(GROUP_ID, getStdlibArtifactId(module, version), "\${$KOTLIN_VERSION_PROPERTY}"),
            MavenArtifactScope.COMPILE,
            null,
            false,
            null
        )
        if (testArtifactId != null) {
            pom.addDependency(
                MavenId(GROUP_ID, testArtifactId, $$"${$$KOTLIN_VERSION_PROPERTY}"),
                MavenArtifactScope.TEST,
                null,
                false,
                null
            )
        }
        if (addJunit) {
            // TODO currently it is always disabled: junit version selection could be shown in the configurator dialog
            pom.addDependency(MavenId("junit", "junit", "4.12"), MavenArtifactScope.TEST, null, false, null)
        }

        val repositoryDescription = getRepositoryForVersion(version)
        if (repositoryDescription != null) {
            pom.addLibraryRepository(repositoryDescription)
            pom.addPluginRepository(repositoryDescription)
        }

        val plugin = pom.addPlugin(MavenId(GROUP_ID, MAVEN_PLUGIN_ID, "\${$KOTLIN_VERSION_PROPERTY}"))
        createExecutions(pom, plugin, module)

        configurePlugin(pom, plugin, module, version)

        CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement<PsiFile>(file)

        collector?.addMessage(KotlinMavenBundle.message("file.was.modified", virtualFile.path))
        return true
    }

    protected open fun configurePlugin(pom: PomFile, plugin: MavenDomPlugin, module: Module, version: IdeKotlinVersion?) {
    }

    protected fun createExecution(
        pomFile: PomFile,
        kotlinPlugin: MavenDomPlugin,
        executionId: String,
        goalName: String,
        module: Module,
        isTest: Boolean
    ) {
        pomFile.addKotlinExecution(module, kotlinPlugin, executionId, PomFile.getPhase(false, isTest), isTest, listOf(goalName))
    }

    override fun updateLanguageVersion(
        module: Module,
        languageVersion: String?,
        apiVersion: String?,
        requiredStdlibVersion: ApiVersion,
        forTests: Boolean
    ) {
        fun doUpdateMavenLanguageVersion(): PsiElement? {
            val psi = findModulePomFile(module) as? XmlFile ?: return null
            val pom = PomFile.forFileOrNull(psi) ?: return null
            return pom.changeLanguageVersion(
                languageVersion,
                apiVersion
            )
        }

        val runtimeUpdateRequired = getRuntimeLibraryVersion(module)?.apiVersion?.let { runtimeVersion ->
            runtimeVersion < requiredStdlibVersion
        } ?: false

        if (runtimeUpdateRequired) {
            Messages.showErrorDialog(
                module.project,
                KotlinMavenBundle.message("update.language.version.feature", requiredStdlibVersion),
                KotlinMavenBundle.message("update.language.version.title")
            )
            return
        }

        val element = doUpdateMavenLanguageVersion()
        if (element == null) {
            Messages.showErrorDialog(
                module.project,
                KotlinMavenBundle.message("error.failed.update.pom"),
                KotlinMavenBundle.message("update.language.version.title")
            )
        } else {
            OpenFileDescriptor(module.project, element.containingFile.virtualFile, element.textRange.startOffset).navigate(true)
        }
    }

    @Deprecated(
        "Please implement/use the KotlinBuildSystemDependencyManager EP instead.",
        replaceWith = ReplaceWith("KotlinBuildSystemDependencyManager.findApplicableConfigurator(module)?.addDependency(module, library.withScope(scope))")
    )
    override fun addLibraryDependency(
        module: Module,
        element: PsiElement,
        library: ExternalLibraryDescriptor,
        libraryJarDescriptor: LibraryJarDescriptor,
        scope: DependencyScope
    ) {
        val scope = OrderEntryFix.suggestScopeByLocation(module, element)
        JavaProjectModelModificationService.getInstance(module.project).addDependency(module, library, scope)
    }

    override fun changeGeneralFeatureConfiguration(
        module: Module,
        feature: LanguageFeature,
        state: LanguageFeature.State,
        forTests: Boolean
    ) {
        val sinceVersion = feature.sinceApiVersion

        val messageTitle = AbstractChangeFeatureSupportLevelFix.getFixText(state, feature.presentableName)
        if (state != LanguageFeature.State.DISABLED && getRuntimeLibraryVersionOrDefault(module).apiVersion < sinceVersion) {
            Messages.showErrorDialog(
                module.project,
                KotlinMavenBundle.message("update.language.version.feature.support", feature.presentableName, sinceVersion),
                messageTitle
            )
            return
        }

        val element = changeMavenFeatureConfiguration(
            module, feature, state, messageTitle
        )

        if (element != null) {
            OpenFileDescriptor(module.project, element.containingFile.virtualFile, element.textRange.startOffset).navigate(true)
        }

    }

    private fun changeMavenFeatureConfiguration(
        module: Module,
        feature: LanguageFeature,
        state: LanguageFeature.State,
        @NlsContexts.DialogTitle messageTitle: String
    ): PsiElement? {
        val psi = findModulePomFile(module) as? XmlFile ?: return null
        val pom = PomFile.forFileOrNull(psi) ?: return null
        val element = pom.changeFeatureConfiguration(feature, state)
        if (element == null) {
            Messages.showErrorDialog(
                module.project,
                KotlinMavenBundle.message("error.failed.update.pom"),
                messageTitle
            )
        }
        return element
    }

    private fun Project.isMavenSyncPending(module: Module): Boolean {
        return KotlinProjectConfigurationService.getInstance(this).isSyncDesired(module)
    }

    private fun Project.isMavenSyncInProgress(): Boolean {
        return KotlinProjectConfigurationService.getInstance(this).isSyncing()
    }

    companion object {
        const val GROUP_ID: String = "org.jetbrains.kotlin"
        const val MAVEN_PLUGIN_ID: String = "kotlin-maven-plugin"
        const val KOTLIN_VERSION_PROPERTY: String = "kotlin.version"

        fun kotlinPluginId(version: String? = null): MavenId =
            MavenId(GROUP_ID, MAVEN_PLUGIN_ID, version)

        fun findModulePomFile(module: Module): PsiFile? {
            val files = MavenProjectsManager.getInstance(module.project).projectsFiles
            for (file in files) {
                val fileModule = ModuleUtilCore.findModuleForFile(file, module.project)
                if (module != fileModule) continue
                val psiFile = PsiManager.getInstance(module.project).findFile(file) ?: continue
                if (!MavenDomUtil.isProjectFile(psiFile)) continue
                if (!canConfigureFile(psiFile)) continue
                return psiFile
            }
            return null
        }

        private fun canConfigureFile(file: PsiFile): Boolean {
            return WritingAccessProvider.isPotentiallyWritable(file.virtualFile, null)
        }

        private fun showErrorMessage(project: Project, @NlsContexts.DialogMessage message: String?) {
            val cantConfigureAutomatically = KotlinMavenBundle.message("error.cant.configure.maven.automatically")
            val seeInstructions = KotlinMavenBundle.message("error.see.installation.instructions")

            Messages.showErrorDialog(
                project,
                "<html>$cantConfigureAutomatically<br/>${if (message != null) "$message</br>" else ""}$seeInstructions</html>",
                KotlinMavenBundle.message("configure.title")
            )
        }
    }
}
