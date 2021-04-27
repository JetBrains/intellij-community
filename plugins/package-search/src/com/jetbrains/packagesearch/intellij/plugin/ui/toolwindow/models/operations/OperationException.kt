package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule

internal sealed class OperationException(
    override val message: String? = "Operation failed: generic failure",
    override val cause: Throwable? = null
) : RuntimeException(message, cause) {

    class UnsupportedBuildSystem(
        projectModule: ProjectModule,
        buildSystemType: BuildSystemType
    ) : OperationException("Unsupported build system '${buildSystemType.name}' in module ${projectModule.name}")

    class InvalidPackage(
        val dependency: UnifiedDependency
    ) : OperationException("Package '${dependency.displayName}' is invalid")

    companion object {

        fun unsupportedBuildSystem(projectModule: ProjectModule) =
            UnsupportedBuildSystem(projectModule, projectModule.buildSystemType)
    }
}
