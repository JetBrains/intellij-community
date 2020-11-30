package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.OperationItem
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository

interface ProjectModuleOperationProvider {

    companion object {

        private val extensionPointName: ExtensionPointName<ProjectModuleOperationProvider> =
            ExtensionPointName.create("com.intellij.packagesearch.projectModuleOperationProvider")

        fun forProjectPsiFile(project: Project, psiFile: PsiFile?): ProjectModuleOperationProvider? =
            extensionPointName.extensions
                .firstOrNull { it.hasSupportFor(project, psiFile) }

        fun forProjectModuleType(projectModuleType: ProjectModuleType): ProjectModuleOperationProvider? =
            extensionPointName.extensions
                .firstOrNull { it.hasSupportFor(projectModuleType) }
    }

    fun hasSupportFor(project: Project, psiFile: PsiFile?): Boolean

    fun hasSupportFor(projectModuleType: ProjectModuleType): Boolean

    fun addDependenciesToProject(
        operationMetadata: DependencyOperationMetadata,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>>

    fun removeDependenciesFromProject(
        operationMetadata: DependencyOperationMetadata,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>>

    fun listDependenciesInProject(
        project: Project,
        virtualFile: VirtualFile
    ): Collection<UnifiedDependency>

    fun addRepositoriesToProject(
        repository: UnifiedDependencyRepository,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>>

    fun listRepositoriesInProject(
        project: Project,
        virtualFile: VirtualFile
    ): Collection<UnifiedDependencyRepository>

    fun refreshProject(project: Project, virtualFile: VirtualFile)
}
