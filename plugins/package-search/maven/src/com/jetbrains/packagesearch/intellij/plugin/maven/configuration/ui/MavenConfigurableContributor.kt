package com.jetbrains.packagesearch.intellij.plugin.maven.configuration.ui

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ConfigurableContributor

internal class MavenConfigurableContributor(private val project: Project) : ConfigurableContributor {

    override fun createDriver() = MavenConfigurableContributorDriver(project)
}
