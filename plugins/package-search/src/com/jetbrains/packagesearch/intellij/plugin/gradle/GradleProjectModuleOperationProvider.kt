package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.buildsystem.model.OperationFailure
import com.intellij.buildsystem.model.OperationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AbstractProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyOperationMetadata
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.jetbrains.packagesearch.intellij.plugin.gradle.configuration.packageSearchGradleConfigurationForProject

private const val EXTENSION_GRADLE = "gradle"
private const val FILENAME_GRADLE_PROPERTIES = "gradle.properties"
private const val FILENAME_GRADLE_WRAPPER_PROPERTIES = "gradle-wrapper.properties"

internal open class GradleProjectModuleOperationProvider : AbstractProjectModuleOperationProvider() {

    override fun hasSupportFor(project: Project, psiFile: PsiFile?): Boolean {
        // Logic based on com.android.tools.idea.gradle.project.sync.GradleFiles.isGradleFile()

        val file = psiFile?.virtualFile ?: return false

        return EXTENSION_GRADLE.equals(file.extension, ignoreCase = true) ||
            FILENAME_GRADLE_PROPERTIES.equals(file.name, ignoreCase = true) ||
            FILENAME_GRADLE_WRAPPER_PROPERTIES.equals(file.name, ignoreCase = true)
    }

    override fun hasSupportFor(projectModuleType: ProjectModuleType): Boolean =
        projectModuleType is GradleProjectModuleType

    override fun addDependencyToModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>> {
        requireNotNull(operationMetadata.newScope) {
            PackageSearchBundle.getMessage("packagesearch.packageoperation.error.gradle.missing.configuration")
        }
        saveAdditionalScopeToConfigurationIfNeeded(module.nativeModule.project, operationMetadata.newScope)

        return super.addDependencyToModule(operationMetadata, module)
    }

    override fun removeDependencyFromModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>> {
        requireNotNull(operationMetadata.currentScope) {
            PackageSearchBundle.getMessage("packagesearch.packageoperation.error.gradle.missing.configuration")
        }
        return super.removeDependencyFromModule(operationMetadata, module)
    }

    private fun saveAdditionalScopeToConfigurationIfNeeded(project: Project, scopeName: String) {
        val configuration = packageSearchGradleConfigurationForProject(project)

        if (!configuration.updateScopesOnUsage) return

        configuration.addGradleScope(scopeName)
    }
}
