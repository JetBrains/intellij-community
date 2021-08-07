package com.jetbrains.packagesearch.intellij.plugin.extensibility

data class RepositoryDeclaration(
    val id: String?,
    val name: String?,
    val url: String?,
    val projectModule: ProjectModule
)
