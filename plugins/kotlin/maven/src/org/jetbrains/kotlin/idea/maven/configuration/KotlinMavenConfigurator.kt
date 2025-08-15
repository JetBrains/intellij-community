// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven.configuration

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix
import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.JavaProjectModelModificationService
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.WritingAccessProvider
import com.intellij.platform.backend.observation.Observation
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
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.*
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersion
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersionOrDefault
import org.jetbrains.kotlin.idea.framework.ui.ConfigureDialogWithModulesAndVersion
import org.jetbrains.kotlin.idea.maven.*
import org.jetbrains.kotlin.idea.projectConfiguration.LibraryJarDescriptor
import org.jetbrains.kotlin.idea.quickfix.AbstractChangeFeatureSupportLevelFix
import org.jetbrains.kotlin.idea.statistics.KotlinJ2KOnboardingFUSCollector
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand

abstract class KotlinMavenConfigurator protected constructor(
    private val testArtifactId: String?,
    private val addJunit: Boolean,
    override val name: String,
    override val presentableText: String
) : KotlinProjectConfigurator {

    override fun getStatus(moduleSourceRootGroup: ModuleSourceRootGroup): ConfigureKotlinStatus {
        val module = moduleSourceRootGroup.baseModule
        if (module.buildSystemType != BuildSystemType.Maven)
            return ConfigureKotlinStatus.NON_APPLICABLE

        val psi = runReadAction { findModulePomFile(module) }
        if (psi == null
            || !psi.isValid
            || psi !is XmlFile
            || psi.virtualFile == null
        ) {
            return ConfigureKotlinStatus.BROKEN
        }

        if (isKotlinModule(module)) {
            return runReadAction { checkPluginConfiguration(module) }
        }
        return ConfigureKotlinStatus.CAN_BE_CONFIGURED
    }

    override fun isApplicable(module: Module): Boolean {
        return module.buildSystemType == BuildSystemType.Maven
    }

    protected open fun checkPluginConfiguration(module: Module): ConfigureKotlinStatus {
        val psi = findModulePomFile(module) as? XmlFile ?: return ConfigureKotlinStatus.BROKEN
        val pom = PomFile.forFileOrNull(psi) ?: return ConfigureKotlinStatus.NON_APPLICABLE

        if (hasKotlinPlugin(pom)) {
            return ConfigureKotlinStatus.CONFIGURED
        }

        val mavenProjectsManager = MavenProjectsManager.getInstance(module.project)
        val mavenProject = mavenProjectsManager.findProject(module) ?: return ConfigureKotlinStatus.BROKEN

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

        KotlinJ2KOnboardingFUSCollector.logStartConfigureKt(project)

        val configuredModules = mutableSetOf<Module>()
        project.executeWriteCommand(KotlinMavenBundle.message("configure.title")) {
            val collector = NotificationMessageCollector.create(project)
            for (module in excludeMavenChildrenModules(project, dialog.modulesToConfigure)) {
                val file = findModulePomFile(module)
                if (file != null) {
                    val configured = configureModule(module, file, IdeKotlinVersion.get(kotlinVersion), collector)
                    if (configured) {
                        OpenFileAction.openFile(file.virtualFile, project)
                        configuredModules.add(module)
                    }
                } else {
                    showErrorMessage(project, KotlinMavenBundle.message("error.cant.find.pom.for.module", module.name))
                }
            }
            collector.showNotification()

            UndoManager.getInstance(project).undoableActionPerformed(object : BasicUndoableAction() {
                override fun undo() {
                    KotlinJ2KOnboardingFUSCollector.logConfigureKtUndone(project)
                }

                override fun redo() {}
            })
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

    protected open fun getMinimumSupportedVersion(): String = "1.0.0"

    protected abstract fun isKotlinModule(module: Module): Boolean
    protected abstract fun isRelevantGoal(goalName: String): Boolean

    protected abstract fun createExecutions(pomFile: PomFile, kotlinPlugin: MavenDomPlugin, module: Module)
    protected abstract fun getStdlibArtifactId(module: Module, version: IdeKotlinVersion): String

    open fun configureModule(module: Module, file: PsiFile, version: IdeKotlinVersion, collector: NotificationMessageCollector): Boolean =
        changePomFile(module, file, version, collector)

    private fun changePomFile(
        module: Module,
        file: PsiFile,
        version: IdeKotlinVersion,
        collector: NotificationMessageCollector
    ): Boolean {
        val virtualFile = file.virtualFile ?: error("Virtual file should exists for psi file " + file.name)
        val project = module.project
        val domModel = MavenDomUtil.getMavenDomProjectModel(project, virtualFile)
        if (domModel == null) {
            showErrorMessage(project, null)
            return false
        }

        val pom = PomFile.forFileOrNull(file as XmlFile) ?: return false
        pom.addProperty(KOTLIN_VERSION_PROPERTY, version.artifactVersion)

        pom.addDependency(
            MavenId(GROUP_ID, getStdlibArtifactId(module, version), "\${$KOTLIN_VERSION_PROPERTY}"),
            MavenArtifactScope.COMPILE,
            null,
            false,
            null
        )
        if (testArtifactId != null) {
            pom.addDependency(MavenId(GROUP_ID, testArtifactId, $$"${$$KOTLIN_VERSION_PROPERTY}"), MavenArtifactScope.TEST, null, false, null)
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

        collector.addMessage(KotlinMavenBundle.message("file.was.modified", virtualFile.path))
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
