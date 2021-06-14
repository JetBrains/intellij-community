package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.openapi.project.Project
import javax.swing.Icon

enum class ProjectModuleTypeTerm {
    SCOPE
}

interface ProjectModuleType {

    val icon: Icon?
    val packageIcon: Icon?
    fun terminologyFor(term: ProjectModuleTypeTerm): String

    fun scopes(project: Project): List<String>
    fun defaultScope(project: Project): String

    // This is a (sad) workaround for IDEA-267229 — when that's sorted, we shouldn't need this anymore.
    @JvmDefault
    fun declaredRepositories(module: ProjectModule): List<UnifiedDependencyRepository> {
        return ProjectModuleOperationProvider.forProjectModuleType(this)?.listRepositoriesInModule(module)?.toList()
            ?: emptyList()
    }
}
