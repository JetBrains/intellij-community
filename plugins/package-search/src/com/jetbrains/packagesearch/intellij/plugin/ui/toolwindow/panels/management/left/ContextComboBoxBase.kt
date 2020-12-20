package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.left

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ClickListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.ui.RiderUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchToolWindowModel
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

abstract class ContextComboBoxBase(protected val viewModel: PackageSearchToolWindowModel) : JPanel() {

    @Suppress("MemberVisibilityCanBePrivate")
    protected val nameLabel = this.createNameLabel()

    @Suppress("MemberVisibilityCanBePrivate")
    protected val valueLabel = this.createValueLabel()

    protected abstract fun createNameLabel(): JLabel
    protected abstract fun createValueLabel(): JLabel

    init {
        setDefaultForeground()
        updateBackground()
        LafManager.getInstance().addLafManagerListener { updateBackground() }
        isFocusable = false
        @Suppress("MagicNumber")
        border = JBUI.Borders.empty(2, 6, 2, 0)

        layout = BoxLayout(this, BoxLayout.X_AXIS)
        add(nameLabel)
        add(valueLabel)
        @Suppress("MagicNumber")
        add(Box.createHorizontalStrut(3)) // Gap before arrow
        add(JLabel(AllIcons.Ide.Statusbar_arrows))

        showPopupMenuOnClick()
        showPopupMenuFromKeyboard()
        indicateHovering()
    }

    protected fun updateLabel() {
        valueLabel.revalidate()
        valueLabel.repaint()
    }

    private fun showPopupMenuFromKeyboard() {
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_DOWN) {
                    showPopupMenu()
                }
            }
        })
    }

    private fun showPopupMenuOnClick() = object : ClickListener() {
        override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
            showPopupMenu()
            return true
        }
    }.installOn(this)

    private fun indicateHovering() {
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) = setOnHoverForeground()
            override fun mouseExited(e: MouseEvent) = setDefaultForeground()
        })
    }

    private fun setDefaultForeground() {
        nameLabel.foreground = if (UIUtil.isUnderDarcula()) UIUtil.getLabelForeground() else UIUtil.getInactiveTextColor()
        valueLabel.foreground = if (UIUtil.isUnderDarcula()) UIUtil.getLabelForeground() else UIUtil.getInactiveTextColor().darker().darker()
    }

    private fun setOnHoverForeground() {
        nameLabel.foreground = if (UIUtil.isUnderDarcula()) UIUtil.getLabelForeground() else UIUtil.getTextAreaForeground()
        valueLabel.foreground = if (UIUtil.isUnderDarcula()) UIUtil.getLabelForeground() else UIUtil.getTextFieldForeground()
    }

    private fun updateBackground() {
        background = RiderUI.HeaderBackgroundColor
    }

    private fun showPopupMenu() = createPopupMenu().showUnderneathOf(this)

    private fun createPopupMenu() = JBPopupFactory.getInstance()
        .createActionGroupPopup(
            null,
            createActionGroup(),
            DataManager.getInstance().getDataContext(this),
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            false
        )

    protected abstract fun createActionGroup(): ActionGroup
}
