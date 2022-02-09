package com.jetbrains.packagesearch.intellij.plugin.gradle.configuration.ui

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ConfigurableContributor

internal class GradleConfigurableContributor(private val project: Project) : ConfigurableContributor {

    override fun createDriver() = GradleConfigurableContributorDriver(project)
}
