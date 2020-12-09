package com.jetbrains.packagesearch.intellij.plugin.extensions.maven

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyOperationMetadata
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.OperationItem
import com.intellij.buildsystem.model.OperationType
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.module.ModuleUtilCore
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AbstractProjectModuleOperationProvider
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenProjectModuleOperationProvider : AbstractProjectModuleOperationProvider() {
    override fun hasSupportFor(projectModuleType: ProjectModuleType): Boolean =
        projectModuleType is MavenProjectModuleType

    override fun hasSupportFor(project: Project, psiFile: PsiFile?) = MavenUtil.isPomFile(project, psiFile?.virtualFile)

    override fun refreshProject(project: Project, virtualFile: VirtualFile) {
        if (!PackageSearchGeneralConfiguration.getInstance(project).refreshProject) return

        val projectsManager = MavenProjectsManager.getInstance(project)
        if (projectsManager.importingSettings.isImportAutomatically) return

        ProjectRefreshingListener.doOnNextChange(projectsManager) {
            projectsManager.forceUpdateProjects(projectsManager.projects)
        }
    }
}

private object ProjectRefreshingListener : MavenProjectsManager.Listener {
    private val runOnNextChange = AtomicReference<(() -> Unit)?>()
    private val needsRegistering = AtomicBoolean(true)

    fun doOnNextChange(mavenProjectsManager: MavenProjectsManager, action: () -> Unit) {
        registerIfNeeded(mavenProjectsManager)
        runOnNextChange.set(action)
    }

    private fun registerIfNeeded(mavenProjectsManager: MavenProjectsManager) {
        if (needsRegistering.getAndSet(false)) {
            mavenProjectsManager.addManagerListener(this)
        }
    }

    override fun projectsScheduled() {
        runOnNextChange.getAndSet(null)?.invoke()
    }
}
