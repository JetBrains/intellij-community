package com.jetbrains.packagesearch.intellij.plugin.extensibility

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
}
