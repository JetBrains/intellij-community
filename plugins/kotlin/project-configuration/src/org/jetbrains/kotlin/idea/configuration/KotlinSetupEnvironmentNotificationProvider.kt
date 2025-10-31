// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.IncompleteModelUtil.isIncompleteModel
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.toModuleGroup
import org.jetbrains.kotlin.idea.base.util.createComponentActionLabel
import org.jetbrains.kotlin.idea.configuration.ui.KotlinConfigurationCheckerService
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinNotConfiguredSuppressedModulesState
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.idea.statistics.KotlinJ2KOnboardingFUSCollector
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.idea.versions.getLibraryRootsWithIncompatibleAbi
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtFile
import java.util.function.Consumer
import java.util.function.Function
import javax.swing.JComponent

// Code is partially copied from com.intellij.codeInsight.daemon.impl.SetupSDKNotificationProvider
class KotlinSetupEnvironmentNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (!Registry.`is`("kotlin.not.configured.show.notification")) {
            return null
        }

        if (!file.isKotlinFileType()) {
            return null
        }

        val psiFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return null
        if (psiFile.language !== KotlinLanguage.INSTANCE) {
            return null
        }

        if (isIncompleteModel(psiFile)) {
            return null
        }

        val module = ModuleUtilCore.findModuleForPsiElement(psiFile) ?: return null

        // In projects with JPS, the following situation occurs when raising a Kotlin stdlib version:
        // The stdlib files are not yet loaded in the .m2 folder when this check happens: the notification shouldn't be shown
        if (module.buildSystemType == BuildSystemType.JPS && !kotlinStdlibExistsOnDiskForJPS(module, project)) return null

        if (!KotlinProjectConfigurationService.getInstance(project).shouldShowNotConfiguredDialog(module)) {
            return null
        }

        if (!ModuleRootManager.getInstance(module).fileIndex.isInSourceContent(file)) {
            return null
        }

        if (ModuleRootManager.getInstance(module).sdk == null && psiFile.platform.isJvm()) {
            return createSetupSdkPanel(project, psiFile)
        }

        val configurationChecker = KotlinConfigurationCheckerService.getInstance(module.project)

        if (!configurationChecker.isSyncing &&
            isNotConfiguredNotificationRequired(module.toModuleGroup()) &&
            !module.hasKotlinPluginEnabled() &&
            !isStdlibModule(module) &&
            getLibraryRootsWithIncompatibleAbi(module).isEmpty()
        ) {
            return createKotlinNotConfiguredPanel(module, getAbleToRunConfigurators(module).toList())
        }

        return null
    }

    // We do this check only for JPS projects because for other build systems this problem is not topical
    private fun kotlinStdlibExistsOnDiskForJPS(module: Module, project: Project): Boolean {
        val moduleDependencies = module.findModuleEntity()?.dependencies ?: return false
        val kotlinStdlibDependencies =
            moduleDependencies.filterIsInstance<LibraryDependency>().filter { it.library.name.contains("kotlin-stdlib") }
        if (kotlinStdlibDependencies.isEmpty()) return false
        return kotlinStdlibDependencies.all {
            dependencyFilesExistOnDisk(it, project)
        }
    }

    private fun dependencyFilesExistOnDisk(dependency: LibraryDependency, project: Project): Boolean {
        val libraryId = dependency.library
        val libraryEntity = project.workspaceModel.currentSnapshot.resolve(libraryId)
        val libraryRoots = libraryEntity?.roots ?: return false
        if (libraryRoots.isEmpty()) return false
        val jarsWithClasses = libraryRoots.filter { it.type == LibraryRootTypeId.COMPILED }
        return jarsWithClasses.all { jar -> VirtualFileManager.getInstance().findFileByUrl(jar.url.url)?.exists() == true }
    }

    companion object {
        private fun createSetupSdkPanel(project: Project, file: PsiFile): Function<in FileEditor, out JComponent?> =
            Function { fileEditor: FileEditor ->
                EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning).apply {
                    text = JavaUiBundle.message("project.sdk.not.defined")
                    createComponentActionLabel(ProjectBundle.message("project.sdk.setup")) { label ->
                        SdkPopupFactory.newBuilder()
                            .withProject(project)
                            .withSdkType(JavaSdk.getInstance())
                            .updateProjectSdkFromSelection()
                            .onSdkSelected(Consumer {
                                runWriteAction {
                                    val module = ModuleUtilCore.findModuleForPsiElement(file)
                                    if (module != null) {
                                        ModuleRootModificationUtil.setSdkInherited(module)
                                    }
                                }
                            })
                            .buildPopup()
                            .showUnderneathOf(label)
                    }
                }
            }

        private fun createKotlinNotConfiguredPanel(module: Module, configurators: List<KotlinProjectConfigurator>): Function<in FileEditor, out JComponent?> =
            Function { fileEditor: FileEditor ->
                KotlinJ2KOnboardingFUSCollector.logShowConfigureKtPanel(module.project)

                EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning).apply {
                text = KotlinProjectConfigurationBundle.message("kotlin.not.configured")
                if (configurators.isNotEmpty()) {
                    val project = module.project
                    createComponentActionLabel(KotlinProjectConfigurationBundle.message("action.text.configure")) { label ->
                        val singleConfigurator = configurators.singleOrNull()
                        if (singleConfigurator != null) {
                            singleConfigurator.apply(project)
                        } else {
                            val configuratorsPopup = createConfiguratorsPopup(project, configurators)
                            configuratorsPopup.showUnderneathOf(label)
                        }
                        KotlinJ2KOnboardingFUSCollector.logClickConfigureKtNotification(project)
                    }

                    createActionLabel(KotlinProjectConfigurationBundle.message("action.text.ignore")) {
                        KotlinNotConfiguredSuppressedModulesState.suppressConfiguration(module)
                        EditorNotifications.getInstance(project).updateAllNotifications()
                    }
                }
            }
        }

        private fun KotlinProjectConfigurator.apply(project: Project) {
            configure(project, emptyList())
            EditorNotifications.getInstance(project).updateAllNotifications()
            checkHideNonConfiguredNotifications(project)
        }

        fun createConfiguratorsPopup(
            project: Project,
            configurators: List<KotlinProjectConfigurator>,
            onConfiguratorApplied: (KotlinProjectConfigurator) -> Unit = {}
        ): ListPopup {
            val step = object : BaseListPopupStep<KotlinProjectConfigurator>(
                KotlinProjectConfigurationBundle.message("title.choose.configurator"),
                configurators
            ) {
                override fun getTextFor(value: KotlinProjectConfigurator?) = value?.presentableText ?: "<none>"

                override fun onChosen(selectedValue: KotlinProjectConfigurator?, finalChoice: Boolean): PopupStep<*>? {
                    return doFinalStep {
                        if (selectedValue == null) return@doFinalStep
                        selectedValue.apply(project)
                        onConfiguratorApplied(selectedValue)
                    }
                }
            }
            return JBPopupFactory.getInstance().createListPopup(step)
        }
    }
}
