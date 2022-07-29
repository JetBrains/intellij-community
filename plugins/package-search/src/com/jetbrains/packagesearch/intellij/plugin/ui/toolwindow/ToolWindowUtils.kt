/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.util.castSafelyTo
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.HasToolWindowActions
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.SimpleToolWindowWithToolWindowActionsPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.SimpleToolWindowWithTwoToolbarsPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import com.jetbrains.packagesearch.intellij.plugin.util.addSelectionChangedListener
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.lookAndFeelFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import kotlin.coroutines.CoroutineContext

internal fun ToolWindow.initialize(project: Project) {
    title = PackageSearchBundle.message("toolwindow.stripe.Dependencies")

    contentManager.addSelectionChangedListener { event ->
        if (this is ToolWindowEx) {
            setAdditionalGearActions(null)
            event.content.component.castSafelyTo<HasToolWindowActions>()
                ?.also { setAdditionalGearActions(it.gearActions) }
        }
        setTitleActions(emptyList())
        event.content.component.castSafelyTo<HasToolWindowActions>()
            ?.titleActions
            ?.also { setTitleActions(it.toList()) }
    }

    isAvailable = false
    contentManager.removeAllContents(true)

    DependenciesToolwindowTabProvider.availableTabsFlow(project)
        .flowOn(project.lifecycleScope.coroutineDispatcher)
        .map { it.map { it.provideTab(project) } }
        .onEach { change ->
            val removedContent = contentManager.contents.filter { it !in change }
            val newContent = change.filter { it !in contentManager.contents }
            removedContent.forEach { contentManager.removeContent(it, true) }
            newContent.forEach { contentManager.addContent(it) }
            isAvailable = change.isNotEmpty()
        }
        .flowOn(Dispatchers.EDT)
        .launchIn(project.lifecycleScope)

    project.lookAndFeelFlow
        .onEach { contentManager.component.updateAndRepaint() }
        .flowOn(Dispatchers.EDT)
        .launchIn(project.lifecycleScope)
}

internal fun PackageSearchPanelBase.initialize(contentFactory: ContentFactory): Content {
    val panelContent = content // should be executed before toolbars
    val toolbar = toolbar
    val topToolbar = topToolbar
    val gearActions = gearActions
    val titleActions = titleActions

    return if (topToolbar == null) {
        createSimpleToolWindowWithToolWindowActionsPanel(title, panelContent, toolbar, gearActions, titleActions, contentFactory, this)
    } else contentFactory.createContent(
        toolbar?.let {
            SimpleToolWindowWithTwoToolbarsPanel(
                it,
                topToolbar,
                gearActions,
                titleActions,
                panelContent
            )
        },
        title,
        false
    ).apply { isCloseable = false }
}

internal fun createSimpleToolWindowWithToolWindowActionsPanel(
    @Nls title: String,
    content: JComponent,
    toolbar: JComponent?,
    gearActions: ActionGroup?,
    titleActions: Array<AnAction>?,
    contentFactory: ContentFactory,
    provider: DataProvider
): Content {
    val createContent = contentFactory.createContent(null, title, false)
    val actionsPanel = SimpleToolWindowWithToolWindowActionsPanel(gearActions, titleActions, false, provider = provider)
    actionsPanel.setProvideQuickActions(true)
    actionsPanel.setContent(content)
    toolbar?.let { actionsPanel.toolbar = it }

    createContent.component = actionsPanel
    createContent.isCloseable = false

    return createContent
}

@Suppress("unused")
internal fun Dispatchers.toolWindowManager(project: Project): CoroutineDispatcher = object : CoroutineDispatcher() {

    private val executor = ToolWindowManager.getInstance(project)

    override fun dispatch(context: CoroutineContext, block: Runnable) = executor.invokeLater(block)
}

fun DependenciesToolwindowTabProvider.isAvailableFlow(project: Project) =
    callbackFlow {
        val sub = addIsAvailableChangesListener(project) { trySend(it) }
        awaitClose { sub.unsubscribe() }
    }
