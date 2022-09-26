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
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.SimpleToolWindowWithToolWindowActionsPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.SimpleToolWindowWithTwoToolbarsPanel
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

internal fun PackageSearchPanelBase.initialize(contentFactory: ContentFactory): Content {
    val panelContent = content // should be executed before toolbars
    val toolbar = toolbar
    val topToolbar = topToolbar
    val gearActions = gearActions
    val titleActions = titleActions

    return if (topToolbar == null) {
        createSimpleToolWindowWithToolWindowActionsPanel(
            title = title,
            content = panelContent,
            toolbar = toolbar,
            gearActions = gearActions,
            titleActions = titleActions,
            contentFactory = contentFactory,
            provider = this
        )
    } else {
        contentFactory.createContent(
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
}

internal fun createSimpleToolWindowWithToolWindowActionsPanel(
    @Nls title: String,
    content: JComponent,
    toolbar: JComponent?,
    gearActions: ActionGroup?,
    titleActions: List<AnAction>,
    contentFactory: ContentFactory,
    provider: DataProvider
): Content {
    val createContent = contentFactory.createContent(null, title, false)
    val actionsPanel = SimpleToolWindowWithToolWindowActionsPanel(
        gearActions = gearActions,
        titleActions = titleActions,
        vertical = false,
        provider = provider
    )
    actionsPanel.setProvideQuickActions(true)
    actionsPanel.setContent(content)
    toolbar?.let { actionsPanel.toolbar = it }

    createContent.component = actionsPanel
    createContent.isCloseable = false

    return createContent
}

