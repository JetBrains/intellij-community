package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.RepositoryDeclaration

internal data class ModuleModel(
    val projectModule: ProjectModule,
    val declaredRepositories: List<RepositoryDeclaration>,
)
