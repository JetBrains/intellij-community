package com.jetbrains.packagesearch.intellij.plugin.extensibility

data class DependencyOperationMetadata(
    val module: ProjectModule,
    val groupId: String,
    val artifactId: String,
    val version: String?,
    val scope: String?
)
