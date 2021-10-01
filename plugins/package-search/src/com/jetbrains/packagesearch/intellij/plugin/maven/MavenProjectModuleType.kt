package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleTypeTerm
import com.jetbrains.packagesearch.intellij.plugin.maven.configuration.packageSearchMavenConfigurationForProject
import icons.OpenapiIcons
import javax.swing.Icon

internal object MavenProjectModuleType : ProjectModuleType {

    override val icon: Icon
        get() = OpenapiIcons.RepositoryLibraryLogo

    override val packageIcon: Icon
        get() = icon

    override fun terminologyFor(term: ProjectModuleTypeTerm): String =
        PackageSearchBundle.message("packagesearch.terminology.dependency.scope")

    override fun defaultScope(project: Project): String =
        packageSearchMavenConfigurationForProject(project).determineDefaultMavenScope()

    override fun scopes(project: Project): List<String> =
        packageSearchMavenConfigurationForProject(project).getMavenScopes()
}
