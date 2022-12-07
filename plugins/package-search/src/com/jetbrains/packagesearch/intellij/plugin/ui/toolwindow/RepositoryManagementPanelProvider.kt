package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow

import com.intellij.dependencytoolwindow.DependenciesToolWindowTabProvider
import com.intellij.dependencytoolwindow.DependenciesToolWindowTabProvider.Subscription
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories.RepositoryManagementPanel
import com.jetbrains.packagesearch.intellij.plugin.util.FeatureFlags
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.packageSearchProjectService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class RepositoryManagementPanelProvider : DependenciesToolWindowTabProvider {

    companion object : DependenciesToolWindowTabProvider.Id

    @Service(Level.PROJECT)
    private class PanelContainer(private val project: Project) {
        val packageManagementPanel by lazy { RepositoryManagementPanel(project).initialize(ContentFactory.getInstance()) }

        val isAvailableFlow =
            combine(
                project.packageSearchProjectService.packageSearchModulesStateFlow.map { it.isNotEmpty() },
                FeatureFlags.showRepositoriesTabFlow
            ) { isServiceReady, isFlagEnabled -> isServiceReady && isFlagEnabled }
                .stateIn(project.lifecycleScope, SharingStarted.Eagerly, false)
    }

    override val id: DependenciesToolWindowTabProvider.Id = Companion

    override fun provideTab(project: Project): Content = project.service<PanelContainer>().packageManagementPanel

    override fun isAvailable(project: Project) = project.service<PanelContainer>().isAvailableFlow.value

    override fun addIsAvailableChangesListener(project: Project, callback: (Boolean) -> Unit): Subscription {
        val job = project.service<PanelContainer>()
            .isAvailableFlow
            .onEach { callback(it) }
            .launchIn(project.lifecycleScope)
        return Subscription { job.cancel() }
    }

}