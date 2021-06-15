package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.RepositoryDeclaration

internal data class ModuleModel(
    val projectModule: ProjectModule,
    val declaredRepositories: List<RepositoryDeclaration>,
) {

    constructor(projectModule: ProjectModule) : this(
        projectModule,
        declaredRepositories(projectModule),
    )
}

private fun declaredRepositories(module: ProjectModule): List<RepositoryDeclaration> {
    return ProjectModuleOperationProvider.forProjectModuleType(module.moduleType)
        ?.listRepositoriesInModule(module)
        ?.map { repository ->
            RepositoryDeclaration(repository.id, repository.name, repository.url, module)
        } ?: emptyList()
}
