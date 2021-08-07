package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.RepositoryDeclaration

internal data class ModuleModel(
    val projectModule: ProjectModule,
    val declaredRepositories: List<RepositoryDeclaration>,
) {

    companion object {

        operator fun invoke(projectModule: ProjectModule) = ModuleModel(
            projectModule = projectModule,
            declaredRepositories = ProjectModuleOperationProvider.forProjectModuleType(projectModule.moduleType)
                ?.listRepositoriesInModule(projectModule)
                ?.map { RepositoryDeclaration(it.id, it.name, it.url, projectModule) }
                ?: emptyList()
        )
    }
}
