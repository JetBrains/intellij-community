package com.jetbrains.packagesearch.intellij.plugin.extensions.gradle.mpp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyOperationMetadata
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.jetbrains.packagesearch.intellij.plugin.extensions.gradle.GradleProjectModuleOperationProvider
import com.jetbrains.packagesearch.patchers.buildsystem.OperationFailure
import com.jetbrains.packagesearch.patchers.buildsystem.OperationItem

class GradleMppProjectModuleOperationProvider : GradleProjectModuleOperationProvider() {

    override fun hasSupportFor(projectModuleType: ProjectModuleType): Boolean =
        projectModuleType is GradleMppProjectModuleType

    override fun addDependenciesToProject(
        operationMetadata: DependencyOperationMetadata,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>> {
        requireNotNull(operationMetadata.scope) { PackageSearchBundle.message("packagesearch.packageoperation.error.gradle.missing.configuration") }

        //val dependenciesToAdd = setOf(
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
        //    gradle.doBatch(removeDependencies = dependenciesToAdd, addDependencies = dependenciesToAdd)
        //        .filter { it.operationType == OperationType.ADD }
        //}
        return emptyList() // TODO use new APIs here instead
    }
}
