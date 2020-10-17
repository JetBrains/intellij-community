package com.jetbrains.packagesearch.intellij.plugin.extensions.gradle

import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.OperationItem
import com.intellij.buildsystem.model.OperationType
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyOperationMetadata
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.jetbrains.packagesearch.intellij.plugin.extensions.gradle.configuration.packageSearchGradleConfigurationForProject
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

private const val EXTENSION_GRADLE = "gradle"
private const val FILENAME_GRADLE_PROPERTIES = "gradle.properties"
private const val FILENAME_GRADLE_WRAPPER_PROPERTIES = "gradle-wrapper.properties"

open class GradleProjectModuleOperationProvider : ProjectModuleOperationProvider {

    override fun hasSupportFor(project: Project, psiFile: PsiFile?): Boolean {
        // Logic based on com.android.tools.idea.gradle.project.sync.GradleFiles.isGradleFile()

        val file = psiFile?.virtualFile ?: return false

        return EXTENSION_GRADLE.equals(file.extension, ignoreCase = true) ||
               FILENAME_GRADLE_PROPERTIES.equals(file.name, ignoreCase = true) ||
               FILENAME_GRADLE_WRAPPER_PROPERTIES.equals(file.name, ignoreCase = true)
    }

    override fun hasSupportFor(projectModuleType: ProjectModuleType): Boolean =
      projectModuleType is GradleProjectModuleType

    override fun addDependenciesToProject(
      operationMetadata: DependencyOperationMetadata,
      project: Project,
      virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>> {
        requireNotNull(operationMetadata.scope) {
            PackageSearchBundle.getMessage("packagesearch.packageoperation.error.gradle.missing.configuration")
        }

        saveAdditionalScopeToConfigurationIfNeeded(project, operationMetadata.scope)

        val dependency = UnifiedDependency(operationMetadata.groupId,
                                           operationMetadata.artifactId,
                                           operationMetadata.version,
                                           operationMetadata.scope)
        try {
            DependencyModifierService.getInstance(project).declaredDependencies(operationMetadata.module.nativeModule)
              .firstOrNull { it.coordinates.groupId == dependency.coordinates.groupId && it.coordinates.artifactId == dependency.coordinates.artifactId }
              ?.also {
                  DependencyModifierService.getInstance(project).updateDependency(operationMetadata.module.nativeModule, it, dependency)
              } ?: DependencyModifierService.getInstance(project).addDependency(operationMetadata.module.nativeModule, dependency)
            return emptyList()
        }
        catch (e: Exception) {
            return listOf(OperationFailure(OperationType.ADD, dependency, e))
        }
    }

    override fun removeDependenciesFromProject(
      operationMetadata: DependencyOperationMetadata,
      project: Project,
      virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>> {
        requireNotNull(operationMetadata.scope) {
            PackageSearchBundle.getMessage("packagesearch.packageoperation.error.gradle.missing.configuration")
        }
        val dependency = UnifiedDependency(operationMetadata.groupId,
                                           operationMetadata.artifactId,
                                           operationMetadata.version,
                                           operationMetadata.scope)
        try {
            DependencyModifierService.getInstance(project).removeDependency(operationMetadata.module.nativeModule, dependency)
            return emptyList()
        }
        catch (e: Exception) {
            return listOf(OperationFailure(OperationType.REMOVE, dependency, e))
        }

    }

    override fun listDependenciesInProject(project: Project, virtualFile: VirtualFile): Collection<UnifiedDependency> {
        val module = ModuleUtilCore.findModuleForFile(virtualFile, project)
        return module?.let { DependencyModifierService.getInstance(project).declaredDependencies(it) } ?: emptyList()
    }

    @Suppress("ComplexMethod")
    override fun addRepositoriesToProject(
      repository: UnifiedDependencyRepository,
      project: Project,
      virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>> {
        val module = ModuleUtilCore.findModuleForFile(virtualFile, project)
        if(module == null) {
            return listOf(OperationFailure(OperationType.ADD, repository, IllegalArgumentException()));
        }
        try {
            DependencyModifierService.getInstance(project).addRepository(module, repository)
            return emptyList()
        }
        catch (e: Exception) {
            return listOf(OperationFailure(OperationType.ADD, repository, e))
        }
    }

    override fun listRepositoriesInProject(project: Project, virtualFile: VirtualFile): Collection<UnifiedDependencyRepository> {
        val module = ModuleUtilCore.findModuleForFile(virtualFile, project)
        return module?.let { DependencyModifierService.getInstance(project).declaredRepositories(it) } ?: emptyList()
    }

    override fun refreshProject(project: Project, virtualFile: VirtualFile) {
        if (!PackageSearchGeneralConfiguration.getInstance(project).refreshProject) return

        if (GradleSettings.getInstance(project).linkedProjectsSettings.any { it.isUseAutoImport }) return

        val module = ModuleUtilCore.findModuleForFile(virtualFile, project)
        val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
        if (rootProjectPath != null) {
            ExternalSystemUtil.refreshProject(
              project, GradleConstants.SYSTEM_ID, rootProjectPath,
              false, ProgressExecutionMode.IN_BACKGROUND_ASYNC
            )
        }
    }

    private fun saveAdditionalScopeToConfigurationIfNeeded(project: Project, scopeName: String) {
        val configuration = packageSearchGradleConfigurationForProject(project)

        if (!configuration.updateScopesOnUsage) return

        configuration.addGradleScope(scopeName)
    }
}
