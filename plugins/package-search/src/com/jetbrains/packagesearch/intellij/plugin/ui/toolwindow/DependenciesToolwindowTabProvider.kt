package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import com.jetbrains.packagesearch.intellij.plugin.extensibility.Subscription
import com.jetbrains.packagesearch.intellij.plugin.util.extensionsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

interface DependenciesToolwindowTabProvider {

    companion object {

        private val extensionPointName: ExtensionPointName<DependenciesToolwindowTabProvider>
            get() = ExtensionPointName.create("com.intellij.packagesearch.dependenciesToolwindowTabProvider")

        internal fun availableTabsFlow(project: Project): Flow<List<DependenciesToolwindowTabProvider>> =
            extensionPointName.extensionsFlow.flatMapLatest { extensions ->
              channelFlow {
                send(extensions.filter { it.isAvailable(project) })
                extensions.map { extension -> extension.isAvailableFlow(project) }
                  .merge()
                  .onEach {
                    val element = extensions.filter { it.isAvailable(project) }
                    send(element)
                  }
                  .launchIn(this)
              }
            }

        internal fun extensions(project: Project) =
            extensionPointName.extensions.toList().filter { it.isAvailable(project) }
    }

    fun provideTab(project: Project): Content

    fun isAvailable(project: Project): Boolean

    fun addIsAvailableChangesListener(project: Project, callback: (Boolean) -> Unit): Subscription
}