package com.jetbrains.packagesearch.intellij.plugin.extensions.maven

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.model.MppModuleType
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleTypeTerm
import com.jetbrains.packagesearch.intellij.plugin.extensions.maven.configuration.packageSearchMavenConfigurationForProject
import icons.OpenapiIcons
import javax.swing.Icon

object MavenProjectModuleType : ProjectModuleType {
    override val icon: Icon?
        get() = OpenapiIcons.RepositoryLibraryLogo

    override val packageIcon: Icon?
        get() = icon

    override fun terminologyFor(term: ProjectModuleTypeTerm): String =
        PackageSearchBundle.message("packagesearch.terminology.dependency.scope")

    override fun defaultScope(project: Project): String =
        packageSearchMavenConfigurationForProject(project).determineDefaultMavenScope()

    override fun scopes(project: Project): List<String> =
        packageSearchMavenConfigurationForProject(project).getMavenScopes()

    override fun providesSupportFor(dependency: StandardV2Package): Boolean {
        // For Maven modules, allow working with any dependency that is not an MPP root
        return dependency.mpp?.moduleType == null || dependency.mpp.moduleType != MppModuleType.ROOT
    }
}
