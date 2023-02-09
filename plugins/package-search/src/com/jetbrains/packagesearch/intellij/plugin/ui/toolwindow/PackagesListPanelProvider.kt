package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow

import com.intellij.dependencytoolwindow.DependenciesToolWindowTabProvider
import com.intellij.dependencytoolwindow.DependenciesToolWindowTabProvider.Subscription
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.PackageManagementPanel
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

internal class PackagesListPanelProvider : DependenciesToolWindowTabProvider {

    companion object : DependenciesToolWindowTabProvider.Id

    internal class PanelContainer(private val project: Project) {
        internal val panel by lazy { PackageManagementPanel(project) }
        internal val content by lazy { panel.initialize(ContentFactory.getInstance()) }
    }

    override val id: DependenciesToolWindowTabProvider.Id = Companion

    override fun provideTab(project: Project): Content = project.service<PanelContainer>().content

    override fun isAvailable(project: Project) = project.packageSearchProjectService.isAvailable

    override fun addIsAvailableChangesListener(project: Project, callback: (Boolean) -> Unit): Subscription {
        val job = project.packageSearchProjectService
            .packageSearchModulesStateFlow
            .onEach { callback(it.isNotEmpty()) }
            .launchIn(project.lifecycleScope)
        return Subscription { job.cancel() }
    }
}