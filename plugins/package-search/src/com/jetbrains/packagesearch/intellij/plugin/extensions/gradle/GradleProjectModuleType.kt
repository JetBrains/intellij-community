package com.jetbrains.packagesearch.intellij.plugin.extensions.gradle

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.model.MppModuleType
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleTypeTerm
import com.jetbrains.packagesearch.intellij.plugin.extensions.gradle.configuration.packageSearchGradleConfigurationForProject
import icons.GradleIcons
import javax.swing.Icon

object GradleProjectModuleType : ProjectModuleType {
    override val icon: Icon?
        get() = GradleIcons.Gradle // TODO use KotlinIcons.MPP if it's a K/MP module

    override val packageIcon: Icon?
        get() = GradleIcons.GradleFile // TODO use KotlinIcons.MPP if it's a K/MP module

    override fun terminologyFor(term: ProjectModuleTypeTerm): String =
        PackageSearchBundle.message("packagesearch.terminology.dependency.configuration")

    override fun defaultScope(project: Project): String =
        packageSearchGradleConfigurationForProject(project).determineDefaultGradleScope()

    override fun scopes(project: Project): List<String> = packageSearchGradleConfigurationForProject(project).getGradleScopes()

    override fun providesSupportFor(dependency: StandardV2Package): Boolean {
        // For Gradle modules, allow working with any dependency that is not an MPP root
        return dependency.mpp?.moduleType == null || dependency.mpp.moduleType != MppModuleType.ROOT
    }
}
