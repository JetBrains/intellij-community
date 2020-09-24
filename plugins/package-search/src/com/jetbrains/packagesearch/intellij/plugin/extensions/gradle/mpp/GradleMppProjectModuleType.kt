package com.jetbrains.packagesearch.intellij.plugin.extensions.gradle.mpp

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.model.MppModuleType
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleTypeTerm
import com.jetbrains.packagesearch.intellij.plugin.extensions.gradle.mpp.configuration.packageSearchGradleMppConfigurationForProject
import javax.swing.Icon
import org.jetbrains.kotlin.idea.KotlinIcons

object GradleMppProjectModuleType : ProjectModuleType {
    override val icon: Icon?
        get() = KotlinIcons.MPP

    override val packageIcon: Icon?
        get() = KotlinIcons.MPP

    override fun terminologyFor(term: ProjectModuleTypeTerm): String =
        PackageSearchBundle.message("packagesearch.terminology.dependency.configuration")

    override fun defaultScope(project: Project): String =
        packageSearchGradleMppConfigurationForProject(project).determineDefaultGradleScope()

    override fun scopes(project: Project): List<String> =
        packageSearchGradleMppConfigurationForProject(project).getGradleScopes()

    override fun providesSupportFor(dependency: StandardV2Package) =
        // For Gradle MPP modules, allow working with any dependency, and for MPP dependencies only allow MPP root
        dependency.mpp?.moduleType == null || dependency.mpp.moduleType == MppModuleType.ROOT
}
