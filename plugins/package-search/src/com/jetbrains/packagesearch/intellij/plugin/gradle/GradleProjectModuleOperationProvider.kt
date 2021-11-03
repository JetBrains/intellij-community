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
import com.jetbrains.packagesearch.intellij.plugin.gradle.configuration.PackageSearchGradleConfiguration

private const val FILE_TYPE_GROOVY = "groovy"
private const val FILE_TYPE_KOTLIN = "kotlin"
private const val EXTENSION_GRADLE = "gradle"
private const val EXTENSION_GRADLE_KTS = "gradle.kts"

internal open class GradleProjectModuleOperationProvider : AbstractProjectModuleOperationProvider() {

    override fun usesSharedPackageUpdateInspection() = true

    override fun hasSupportFor(project: Project, psiFile: PsiFile?): Boolean {
        // Logic based on com.android.tools.idea.gradle.project.sync.GradleFiles.isGradleFile()
        val file = psiFile?.virtualFile ?: return false

        val isGroovyFile = FILE_TYPE_GROOVY.equals(psiFile.fileType.name, ignoreCase = true)
        val isKotlinFile = FILE_TYPE_KOTLIN.equals(psiFile.fileType.name, ignoreCase = true)

        if (!isGroovyFile && !isKotlinFile) return false
        return file.name.endsWith(EXTENSION_GRADLE, ignoreCase = true) || file.name.endsWith(EXTENSION_GRADLE_KTS, ignoreCase = true)
    }

    override fun hasSupportFor(projectModuleType: ProjectModuleType): Boolean =
        projectModuleType is GradleProjectModuleType

    override fun addDependencyToModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>> {
        requireNotNull(operationMetadata.newScope) {
            PackageSearchBundle.getMessage("packagesearch.operation.error.gradle.missing.configuration")
        }
        saveAdditionalScopeToConfigurationIfNeeded(module.nativeModule.project, operationMetadata.newScope)

        return super.addDependencyToModule(operationMetadata, module)
    }

    override fun removeDependencyFromModule(
        operationMetadata: DependencyOperationMetadata,
        module: ProjectModule
    ): List<OperationFailure<out OperationItem>> {
        requireNotNull(operationMetadata.currentScope) {
            PackageSearchBundle.getMessage("packagesearch.operation.error.gradle.missing.configuration")
        }
        return super.removeDependencyFromModule(operationMetadata, module)
    }

    private fun saveAdditionalScopeToConfigurationIfNeeded(project: Project, scopeName: String) {
        val configuration = PackageSearchGradleConfiguration.getInstance(project)
        if (configuration.updateScopesOnUsage) {
            configuration.addGradleScope(scopeName)
        }
    }
}
