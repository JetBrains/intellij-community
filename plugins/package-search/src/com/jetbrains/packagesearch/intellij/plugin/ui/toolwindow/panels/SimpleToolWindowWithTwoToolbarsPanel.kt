package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.ui.JBColor
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Graphics
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import javax.swing.JComponent
import javax.swing.JPanel
import org.jetbrains.annotations.NonNls

class SimpleToolWindowWithTwoToolbarsPanel(
    val leftToolbar: JComponent,
    val topToolbar: JComponent,
    val content: JComponent
) : JPanel(), QuickActionProvider, DataProvider {

    private var myProvideQuickActions: Boolean = false

    init {
        layout = BorderLayout(1, 1)
        myProvideQuickActions = true
        addContainerListener(object : ContainerAdapter() {
            override fun componentAdded(e: ContainerEvent?) {
                val child = e!!.child
                if (child is Container) {
                    child.addContainerListener(this)
                }
            }

            override fun componentRemoved(e: ContainerEvent?) {
                val child = e!!.child
                if (child is Container) {
                    child.removeContainerListener(this)
                }
            }
        })
        add(leftToolbar, BorderLayout.WEST)
        add(JPanel(BorderLayout()).apply {
            add(topToolbar, BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
        }, BorderLayout.CENTER)
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

    override fun getComponent(): JComponent? {
        return this
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        val x = leftToolbar.bounds.maxX.toInt()
        val y = topToolbar.bounds.maxY.toInt()
        g.apply {
            color = JBColor.border()
            drawLine(0, y, width, y)
            drawLine(x, 0, x, height)
        }
    }

    fun extractActions(c: JComponent): List<AnAction> = UIUtil.uiTraverser(c)
        .traverse()
        .filter(ActionToolbar::class.java)
        .flatten { toolbar -> toolbar.actions }
        .toList()
}
