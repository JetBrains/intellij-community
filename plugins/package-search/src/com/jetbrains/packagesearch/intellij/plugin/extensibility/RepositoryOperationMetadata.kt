package com.jetbrains.packagesearch.intellij.plugin.extensibility

data class RepositoryOperationMetadata(
    val module: ProjectModule,
    val groupId: String,
    val artifactId: String,
    val currentVersion: String?,
    val currentScope: String?,
    val newVersion: String? = null,
    val newScope: String? = null
) {

    @Suppress("DuplicatedCode")
    val displayName by lazy {
        buildString {
            append(groupId)
            append(":$artifactId")
            if (currentVersion != null) append(":$currentVersion")
            append(" [module='")
            append(module.getFullName())
            if (currentScope != null) {
                append("', currentScope='")
                append(currentScope)
            }
            if (newScope != null) {
                append("', newScope='")
                append(newScope)
            }
            if (newVersion != null) {
                append("', newVersion='")
                append(newVersion)
            }
            append("']")
        }
    }
}
