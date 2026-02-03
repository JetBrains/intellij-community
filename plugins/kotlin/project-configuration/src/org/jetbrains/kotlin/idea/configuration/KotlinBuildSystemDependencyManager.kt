// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface KotlinBuildSystemDependencyManager {
    companion object {
        val EP_NAME: ExtensionPointName<KotlinBuildSystemDependencyManager> =
            ExtensionPointName.create("org.jetbrains.kotlin.buildSystemDependencyManager")

        fun findApplicableConfigurator(module: Module): KotlinBuildSystemDependencyManager? {
            return module.project.extensionArea.getExtensionPoint(EP_NAME).extensionList.firstOrNull { it.isApplicable(module) }
        }
    }

    /**
     * Returns if the [KotlinBuildSystemDependencyManager] can be used to add dependencies to the [module].
     */
    fun isApplicable(module: Module): Boolean

    /**
     * Adds the [libraryDescriptor] as an external dependency.
     * Must be called from a write command.
     */
    fun addDependency(module: Module, libraryDescriptor: ExternalLibraryDescriptor)

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