package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleTypeTerm
import com.jetbrains.packagesearch.intellij.plugin.maven.configuration.PackageSearchMavenConfiguration
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
        PackageSearchMavenConfiguration.getInstance(project).determineDefaultMavenScope()

    override fun userDefinedScopes(project: Project): List<String> =
        PackageSearchMavenConfiguration.getInstance(project).getMavenScopes()
}
