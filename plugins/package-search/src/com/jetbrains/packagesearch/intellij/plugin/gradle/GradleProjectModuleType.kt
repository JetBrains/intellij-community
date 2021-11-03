package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleTypeTerm
import com.jetbrains.packagesearch.intellij.plugin.gradle.configuration.PackageSearchGradleConfiguration
import icons.GradleIcons
import javax.swing.Icon

internal object GradleProjectModuleType : ProjectModuleType {

    override val icon: Icon
        get() = GradleIcons.Gradle // TODO use KotlinIcons.MPP if it's a K/MP module

    override val packageIcon: Icon
        get() = GradleIcons.GradleFile // TODO use KotlinIcons.MPP if it's a K/MP module

    override fun terminologyFor(term: ProjectModuleTypeTerm): String =
        PackageSearchBundle.message("packagesearch.terminology.dependency.configuration")

    override fun defaultScope(project: Project): String =
        PackageSearchGradleConfiguration.getInstance(project).determineDefaultGradleScope()

    override fun userDefinedScopes(project: Project): List<String> =
        PackageSearchGradleConfiguration.getInstance(project).getGradleScopes()
}
