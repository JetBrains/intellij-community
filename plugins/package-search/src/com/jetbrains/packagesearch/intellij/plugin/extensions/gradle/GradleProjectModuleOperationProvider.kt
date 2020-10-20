package com.jetbrains.packagesearch.intellij.plugin.extensions.gradle

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
import com.jetbrains.packagesearch.patchers.buildsystem.OperationFailure
import com.jetbrains.packagesearch.patchers.buildsystem.OperationItem
import com.jetbrains.packagesearch.patchers.buildsystem.unified.UnifiedDependency
import com.jetbrains.packagesearch.patchers.buildsystem.unified.UnifiedDependencyRepository
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

        //val dependenciesToAdd = setOf(
        //    //GradleDependency(
        //    //    GradleRemoteCoordinates.StringRemoteCoordinates(
        //    //        operationMetadata.groupId,
        //    //        operationMetadata.artifactId,
        //    //        operationMetadata.version
        //    //    ),
        //    //    operationMetadata.scope
        //    //)
        //)
        //
        //return Gradle(PackageSearchVirtualFileAccess(project, virtualFile))
        //    .doBatch(removeDependencies = dependenciesToAdd, addDependencies = dependenciesToAdd)
        //    .filter { it.operationType == OperationType.ADD }
        return emptyList() // TODO use new APIs here instead
    }

    override fun removeDependenciesFromProject(
        operationMetadata: DependencyOperationMetadata,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>> {
        requireNotNull(operationMetadata.scope) {
            PackageSearchBundle.getMessage("packagesearch.packageoperation.error.gradle.missing.configuration")
        }

        //val dependenciesToRemove = setOf(
        //    GradleDependency(
        //        GradleRemoteCoordinates.StringRemoteCoordinates(
        //            operationMetadata.groupId,
        //            operationMetadata.artifactId,
        //            operationMetadata.version
        //        ),
        //        operationMetadata.scope
        //    )
        //)
        //
        //return parseGradleGroovyBuildScriptFrom(project, virtualFile) { gradle ->
        //    gradle.doBatch(removeDependencies = dependenciesToRemove)
        //}
        return emptyList() // TODO use new APIs here instead
    }

    override fun listDependenciesInProject(project: Project, virtualFile: VirtualFile): Collection<UnifiedDependency> {
        return emptyList() // TODO use new APIs here instead
    }

    @Suppress("ComplexMethod")
    override fun addRepositoriesToProject(
        repository: UnifiedDependencyRepository,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>> {
        //
        //val gradleRepository =
        //    when {
        //        repository.id != null && repository.id == "maven_central" -> {
        //            GradleMavenRepository.MavenCentral
        //        }
        //        repository.url != null &&
        //            (repository.url!!.contains("://repo1.maven.org") ||
        //                repository.url!!.contains("://repo.maven.apache.org")) -> {
        //            GradleMavenRepository.MavenCentral
        //        }
        //        repository.id != null && repository.id == "gmaven" -> {
        //            GradleMavenRepository.Google
        //        }
        //        repository.url != null && repository.url!!.contains("://maven.google.com") -> {
        //            GradleMavenRepository.Google
        //        }
        //        repository.id != null && repository.id == "jcenter" -> {
        //            GradleMavenRepository.JCenter
        //        }
        //        repository.url != null && repository.url!!.contains("://jcenter.bintray.com") -> {
        //            GradleMavenRepository.JCenter
        //        }
        //        else -> {
        //            GradleMavenRepository.Generic(repository.url!!)
        //        }
        //    }
        //
        //return parseGradleGroovyBuildScriptFrom(project, virtualFile) { gradle ->
        //    if (!gradle.listRepositories().any { it.isEquivalentTo(gradleRepository) }) {
        //        gradle.doBatch(addRepositories = setOf(gradleRepository))
        //    } else {
        //        emptyList()
        //    }
        //}
        return emptyList() // TODO use new APIs here instead
    }

    override fun listRepositoriesInProject(project: Project, virtualFile: VirtualFile): Collection<UnifiedDependencyRepository> {
        //val repositories = parseGradleGroovyBuildScriptFrom(project, virtualFile) { gradle ->
        //    gradle.listRepositories()
        //}
        //return repositories.map { GradleUnifiedDependencyRepositoryConverter.convert(it) }
        return emptyList() // TODO use new APIs here instead
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
