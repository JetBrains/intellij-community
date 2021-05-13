package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.OperationItem
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.extensions.gradle.GradleProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensions.maven.MavenProjectModuleOperationProvider

/**
 * Extension point that allows to modify the dependencies of a specific project.
 * For an implementation examples check out [GradleProjectModuleOperationProvider]
 * or [MavenProjectModuleOperationProvider].
 */
interface ProjectModuleOperationProvider {

    companion object {

        private val extensionPointName: ExtensionPointName<ProjectModuleOperationProvider> =
            ExtensionPointName.create("com.intellij.packagesearch.projectModuleOperationProvider")

        /**
         * Retrieves the first provider for given [project] and [psiFile].
         * @return The first compatible provider or `null` if none is found.
         */
        fun forProjectPsiFile(project: Project, psiFile: PsiFile?): ProjectModuleOperationProvider? =
            extensionPointName.extensions
                .firstOrNull { it.hasSupportFor(project, psiFile) }

        /**
         * Retrieves the first provider for given the [projectModuleType].
         * @return The first compatible provider or `null` if none is found.
         */
        fun forProjectModuleType(projectModuleType: ProjectModuleType): ProjectModuleOperationProvider? =
            extensionPointName.extensions
                .firstOrNull { it.hasSupportFor(projectModuleType) }
    }

    /**
     * Checks if current implementation has support the given [project] for the current [psiFile].
     * @return `true` if the [project] and [psiFile] are supported.
     */
    fun hasSupportFor(project: Project, psiFile: PsiFile?): Boolean

    /**
     * Checks if current implementation has support the given [projectModuleType].
     * @return `true` if the [projectModuleType] is supported.
     */
    fun hasSupportFor(projectModuleType: ProjectModuleType): Boolean

    /**
     * Adds a dependency to the given [project] using [operationMetadata] by modifying the given [virtualFile].
     * @return A list containing all failures encountered during the operation. If the list is empty, the operation was successful.
     */
    fun addDependencyToProject(
        operationMetadata: DependencyOperationMetadata,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>>

    /**
     * Removes a dependency from the given [project] using [operationMetadata] by modifying the given [virtualFile].
     * @return A list containing all failures encountered during the operation. If the list is empty, the operation was successful.
     */
    fun removeDependencyFromProject(
        operationMetadata: DependencyOperationMetadata,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>>

    /**
     * Modify a dependency in the given [project] using [operationMetadata] by modifying the given [virtualFile].
     * @return A list containing all failures encountered during the operation. If the list is empty, the operation was successful.
     */
    fun updateDependencyInProject(
        operationMetadata: DependencyOperationMetadata,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>>

    /**
     * Lists all dependencies in the given [virtualFile] in the given [project].
     * @return A [Collection]<[UnifiedDependency]> found the project.
     */
    fun listDependenciesInProject(
        project: Project,
        virtualFile: VirtualFile
    ): Collection<UnifiedDependency>

    /**
     * Adds the [repository] to the given [project] by modifying the given [virtualFile].
     * @return A list containing all failures encountered during the operation. If the list is empty, the operation was successful.
     */
    fun addRepositoryToProject(
        repository: UnifiedDependencyRepository,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>>

    /**
     * Removes the [repository] from the given [project] by modifying the given [virtualFile].
     * @return A list containing all failures encountered during the operation. If the list is empty, the operation was successful.
     */
    fun removeRepositoryFromProject(
        repository: UnifiedDependencyRepository,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>>

    /**
     * Lists all repositories in the given [virtualFile] in the given [project].
     * @return A [Collection]<[UnifiedDependencyRepository]> found the project.
     */
    fun listRepositoriesInProject(
        project: Project,
        virtualFile: VirtualFile
    ): Collection<UnifiedDependencyRepository>

    /**
     * Refreshes the project by triggering the build system sync with IntelliJ.
     */
    fun refreshProject(project: Project, virtualFile: VirtualFile)
}
