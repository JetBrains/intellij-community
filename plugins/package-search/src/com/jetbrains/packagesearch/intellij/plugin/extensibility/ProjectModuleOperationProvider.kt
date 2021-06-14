package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.OperationItem
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Extension point that allows to modify the dependencies of a specific project.
 */
interface ProjectModuleOperationProvider {

    companion object {

        private val extensionPointName: ExtensionPointName<ProjectModuleOperationProvider> =
            ExtensionPointName.create("com.intellij.packagesearch.projectModuleOperationProvider")

        /**
         * Retrieves the first provider for given [project] and [psiFile].
         * @return The first compatible provider or `null` if none is found.
         */
        fun forProjectPsiFileOrNull(project: Project, psiFile: PsiFile?): ProjectModuleOperationProvider? =
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
     * Adds a dependency to the given [module] using [operationMetadata].
     * @return A list containing all failures encountered during the operation. If the list is empty, the operation was successful.
     */
    fun addDependencyToModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>>

    /**
     * Removes a dependency from the given [module] using [operationMetadata].
     * @return A list containing all failures encountered during the operation. If the list is empty, the operation was successful.
     */
    fun removeDependencyFromModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>>

    /**
     * Modify a dependency in the given [module] using [operationMetadata].
     * @return A list containing all failures encountered during the operation. If the list is empty, the operation was successful.
     */
    fun updateDependencyInModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>>

    /**
     * Lists all dependencies in the given [module].
     * @return A [Collection]<[UnifiedDependency]> found in the project.
     */
    fun listDependenciesInModule(
        module: ProjectModule
    ): Collection<UnifiedDependency>

    /**
     * Adds the [repository] to the given [module].
     * @return A list containing all failures encountered during the operation. If the list is empty, the operation was successful.
     */
    fun addRepositoryToModule(
        repository: UnifiedDependencyRepository,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>>

    /**
     * Removes the [repository] from the given [module].
     * @return A list containing all failures encountered during the operation. If the list is empty, the operation was successful.
     */
    fun removeRepositoryFromModule(
        repository: UnifiedDependencyRepository,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>>

    /**
     * Lists all repositories in the given [module].
     * @return A [Collection]<[UnifiedDependencyRepository]> found the project.
     */
    fun listRepositoriesInModule(
        module: ProjectModule
    ): Collection<UnifiedDependencyRepository>
}
