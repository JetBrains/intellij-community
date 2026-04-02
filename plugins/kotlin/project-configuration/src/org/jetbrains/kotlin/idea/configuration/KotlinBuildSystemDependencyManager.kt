// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.codeInspection.options.OptionController
import com.intellij.codeInspection.options.OptionControllerProvider
import com.intellij.modcommand.ModCommand
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
interface KotlinBuildSystemDependencyManager {
    companion object {
        val EP_NAME: ExtensionPointName<KotlinBuildSystemDependencyManager> =
            ExtensionPointName.create("org.jetbrains.kotlin.buildSystemDependencyManager")

        fun findApplicableConfigurator(module: Module): KotlinBuildSystemDependencyManager? =
            module.project.extensionArea.getExtensionPoint(EP_NAME).extensionList.firstOrNull { it.isApplicable(module) }

        @TestOnly
        inline fun <reified T : KotlinBuildSystemDependencyManager> findConfigurator(project: Project): T =
            project.extensionArea.getExtensionPoint(EP_NAME).extensionList.filterIsInstance<T>().firstOrNull()
                ?: error("No ${T::class.java} configurator found")
    }

    /**
     * Returns if the [KotlinBuildSystemDependencyManager] can be used to add dependencies to the [module].
     */
    fun isApplicable(module: Module): Boolean

    /**
     * Adds the [libraryDescriptor] as an external dependency.
     *
     * @return a [Job] that completes when the dependency is added.
     */
    @RequiresBackgroundThread
    @Deprecated("Use addDependencyModCommand instead", ReplaceWith("addDependencyModCommand(context, module, libraryDescriptor)"))
    fun addDependency(module: Module, libraryDescriptor: ExternalLibraryDescriptor): Job

    fun addDependencyModCommand(contextFile: PsiFile, module: Module, libraryDescriptor: ExternalLibraryDescriptor): ModCommand =
        ModCommand.nop()

    /**
     * Returns the build script file for the [module] if the build system has build script files.
     * Must be called from a read action.
     */
    fun getBuildScriptFile(module: Module): VirtualFile?

    /**
     * Returns if there is a sync of the [project] pending to reload the build system described by this [KotlinBuildSystemDependencyManager].
     */
    fun isProjectSyncPending(): Boolean

    /**
     * Returns if a sync of the build system described by this [KotlinBuildSystemDependencyManager] is in progress for the [project].
     */
    fun isProjectSyncInProgress(): Boolean

    /**
     * Starts a reload of the build system, usually done to load dependencies after adding them.
     * Implementations will usually not wait for the sync to finish before returning.
     */
    fun startProjectSync()
}

fun ExternalLibraryDescriptor.withScope(newScope: DependencyScope): ExternalLibraryDescriptor = ExternalLibraryDescriptor(
    libraryGroupId,
    libraryArtifactId,
    minVersion,
    maxVersion,
    preferredVersion,
    newScope
)

@ApiStatus.Internal
fun KotlinBuildSystemDependencyManager.isProjectSyncPendingOrInProgress(): Boolean {
    return isProjectSyncPending() || isProjectSyncInProgress()
}

class KotlinDependencyProvider : OptionControllerProvider {
    override fun forContext(context: PsiElement): OptionController {
        val project = context.project
        val configurationService = KotlinProjectConfigurationService.getInstance(project)
        return OptionController.empty()
            .onValue<ExternalLibraryDescriptor>(
                "library",
                {
                    ExternalLibraryDescriptor("", "")
                },
                setter@{ libraryDescriptor ->
                    val module = ModuleUtilCore.findModuleForPsiElement(context) ?: return@setter
                    val dependencyManager =
                        KotlinBuildSystemDependencyManager.findApplicableConfigurator(module)
                            ?.takeUnless { it.isProjectSyncPendingOrInProgress() } ?: return@setter

                    val job = configurationService.coroutineScope.launchTracked {
                        val addDependencyJob = dependencyManager.addDependency(module, libraryDescriptor)
                        addDependencyJob.join()

                        dependencyManager.startProjectSync()

                        withContext(Dispatchers.EDT) {
                            dependencyManager.getBuildScriptFile(module)?.let { buildScriptFile ->
                                FileEditorManager.getInstance(module.project).openFile(buildScriptFile, false)
                            }
                        }
                    }
                    jobReference?.set(job)
                })
            .onValue<Boolean>(
                "sync",
                {
                    false
                },
                setter@{
                    val module = ModuleUtilCore.findModuleForPsiElement(context) ?: return@setter
                    val dependencyManager =
                        KotlinBuildSystemDependencyManager.findApplicableConfigurator(module) ?: return@setter
                    val job = configurationService.coroutineScope.launchTracked {
                        dependencyManager.startProjectSync()
                    }
                    jobReference?.set(job)
                }
            )
    }

    override fun name(): @NonNls String = NAME

    @VisibleForTesting
    var jobReference: AtomicReference<Job>? = null

    companion object {
        const val NAME: String = "KotlinDependencyProvider"

        @TestOnly
        @JvmStatic
        fun getInstance(): KotlinDependencyProvider =
            (OptionControllerProvider.EP_NAME.extensionList.firstOrNull { it.name() == NAME } as? KotlinDependencyProvider
                ?: error("KotlinDependencyProvider is not found"))

        @JvmStatic
        fun syncModCommand(element: PsiElement): ModCommand =
            ModCommand.updateOption(element, "$NAME.sync", true)

        @JvmStatic
        fun addLibraryModCommand(element: PsiElement, libraryDescriptor: ExternalLibraryDescriptor): ModCommand =
            ModCommand.updateOption(element, "$NAME.library", libraryDescriptor)
    }
}
