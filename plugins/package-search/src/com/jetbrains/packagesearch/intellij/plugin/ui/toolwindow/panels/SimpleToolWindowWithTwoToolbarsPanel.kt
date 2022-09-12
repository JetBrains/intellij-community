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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels

import com.intellij.dependencytoolwindow.HasToolWindowActions
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Graphics
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import javax.swing.JComponent
import javax.swing.JPanel

internal class SimpleToolWindowWithTwoToolbarsPanel(
    private val leftToolbar: JComponent,
    private val topToolbar: JComponent,
    override val gearActions: ActionGroup?,
    override val titleActions: List<AnAction>,
    val content: JComponent
) : JPanel(), QuickActionProvider, DataProvider, HasToolWindowActions {

    private var myProvideQuickActions: Boolean = false

    init {
        layout = BorderLayout(1, 1)
        myProvideQuickActions = true
        addContainerListener(object : ContainerAdapter() {
            override fun componentAdded(e: ContainerEvent) {
                val child = e.child
                if (child is Container) {
                    child.addContainerListener(this)
                }
            }

            override fun componentRemoved(e: ContainerEvent) {
                val child = e.child
                if (child is Container) {
                    child.removeContainerListener(this)
                }
            }
        })
        add(leftToolbar, BorderLayout.WEST)
        add(
            JPanel(BorderLayout()).apply {
                add(topToolbar, BorderLayout.NORTH)
                add(content, BorderLayout.CENTER)
            },
            BorderLayout.CENTER
        )
        revalidate()
        repaint()
    }

    override fun getData(@NonNls dataId: String) = when {
        QuickActionProvider.KEY.`is`(dataId) && myProvideQuickActions -> this
        else -> null
    }

    override fun getActions(originalProvider: Boolean): List<AnAction> {
        val actions = ArrayList<AnAction>()
        actions.addAll(extractActions(topToolbar))
        actions.addAll(extractActions(leftToolbar))
        return actions
    }

    override fun getComponent(): JComponent = this

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        val x = leftToolbar.bounds.maxX.toInt()
        val y = topToolbar.bounds.maxY.toInt()
        g.apply {
            color = PackageSearchUI.Colors.border
            drawLine(0, y, width, y)
            drawLine(x, 0, x, height)
        }
    }

    private fun extractActions(c: JComponent): List<AnAction> = UIUtil.uiTraverser(c)
        .traverse()
        .filter(ActionToolbar::class.java)
        .flatten { toolbar -> toolbar.actions }
        .toList()
}
