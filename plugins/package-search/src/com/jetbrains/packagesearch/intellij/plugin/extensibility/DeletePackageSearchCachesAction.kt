package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.ide.actions.cache.AsyncRecoveryResult
import com.intellij.ide.actions.cache.RecoveryAction
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchApplicationCaches
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectCachesService
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import kotlinx.coroutines.future.future

class DeletePackageSearchProjectCachesAction : RecoveryAction {

    override val performanceRate: Int
        get() = 2000
    override val presentableName: String
        get() = PackageSearchBundle.message("packagesearch.configuration.recovery.caches.global")
    override val actionKey: String
        get() = "pkgs-delete-project-caches"

    override fun perform(project: Project) = project.lifecycleScope.future {
        packageSearchApplicationCaches.clear()
        project.packageSearchProjectService.restart()
        AsyncRecoveryResult(project, emptyList())
    }
}

class DeletePackageSearchCachesAction : RecoveryAction {

    override val performanceRate: Int
        get() = 4000
    override val presentableName: String
        get() = PackageSearchBundle.message("packagesearch.configuration.recovery.caches")
    override val actionKey: String
        get() = "pkgs-delete-app-caches"

    override fun perform(project: Project) = project.lifecycleScope.future {
        project.packageSearchProjectCachesService.clear()
        project.packageSearchProjectService.restart()
        AsyncRecoveryResult(project, emptyList())
    }
}
