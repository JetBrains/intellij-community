package com.jetbrains.packagesearch.intellij.plugin.extensibility

internal data class RepositoryDeclaration(
    val id: String?,
    val name: String?,
    val url: String?,
    val projectModule: ProjectModule
)
