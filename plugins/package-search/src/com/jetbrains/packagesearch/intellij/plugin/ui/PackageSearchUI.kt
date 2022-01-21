package com.jetbrains.packagesearch.intellij.plugin.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.components.BrowsableLinkLabel
import org.jetbrains.annotations.Nls
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Rectangle
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.Scrollable

object PackageSearchUI {

    private val MAIN_BG_COLOR: Color = JBColor.namedColor("Plugins.background", UIUtil.getListBackground())

    internal val GRAY_COLOR: Color = JBColor.namedColor("Label.infoForeground", JBColor(Gray._120, Gray._135))

    internal val HeaderBackgroundColor = MAIN_BG_COLOR
    internal val SectionHeaderBackgroundColor = JBColor.namedColor("Plugins.SectionHeader.background", JBColor(0xF7F7F7, 0x3C3F41))
    internal val UsualBackgroundColor = MAIN_BG_COLOR
    internal val ListRowHighlightBackground = JBColor(0xF2F5F9, 0x4C5052)
    internal val InfoBannerBackground = JBColor(0xE6EEF7, 0x1C3956)

    internal val MediumHeaderHeight = JBValue.Float(30f)
    internal val SmallHeaderHeight = JBValue.Float(24f)

    @Suppress("MagicNumber") // Thanks, Swing
    internal fun headerPanel(init: BorderLayoutPanel.() -> Unit) = object : BorderLayoutPanel() {
        init {
            border = JBEmptyBorder(2, 0, 2, 12)
            init()
        }

        override fun getBackground() = HeaderBackgroundColor
    }

    internal fun cardPanel(cards: List<JPanel> = emptyList(), backgroundColor: Color = UsualBackgroundColor, init: JPanel.() -> Unit) =
        object : JPanel() {
            init {
                layout = CardLayout()
                cards.forEach { add(it) }
                init()
            }

            override fun getBackground() = backgroundColor
        }

    internal fun borderPanel(backgroundColor: Color = UsualBackgroundColor, init: BorderLayoutPanel.() -> Unit) = object : BorderLayoutPanel() {

        init {
            init()
        }

        override fun getBackground() = backgroundColor
    }

    internal fun boxPanel(axis: Int = BoxLayout.Y_AXIS, backgroundColor: Color = UsualBackgroundColor, init: JPanel.() -> Unit) =
        object : JPanel() {
            init {
                layout = BoxLayout(this, axis)
                init()
            }

            override fun getBackground() = backgroundColor
        }

    internal fun flowPanel(backgroundColor: Color = UsualBackgroundColor, init: JPanel.() -> Unit) = object : JPanel() {
        init {
            layout = FlowLayout(FlowLayout.LEFT)
            init()
        }

        override fun getBackground() = backgroundColor
    }

    fun checkBox(@Nls title: String, init: JCheckBox.() -> Unit = {}) = object : JCheckBox(title) {

        init {
            init()
        }

        override fun getBackground() = UsualBackgroundColor
    }

    fun textField(init: JTextField.() -> Unit): JTextField = JTextField().apply {
        init()
    }

    internal fun menuItem(@Nls title: String, icon: Icon?, handler: () -> Unit): JMenuItem {
        if (icon != null) {
            return JMenuItem(title, icon).apply { addActionListener { handler() } }
        }
        return JMenuItem(title).apply { addActionListener { handler() } }
    }

    fun createLabel(@Nls text: String? = null, init: JLabel.() -> Unit = {}) = JLabel().apply {
        font = StartupUiUtil.getLabelFont()
        if (text != null) this.text = text
        init()
    }

    internal fun createLabelWithLink(init: BrowsableLinkLabel.() -> Unit = {}) = BrowsableLinkLabel().apply {
        font = StartupUiUtil.getLabelFont()
        init()
    }

    internal fun getTextColorPrimary(isSelected: Boolean = false): Color = when {
        isSelected -> JBColor.lazy { UIUtil.getListSelectionForeground(true) }
        else -> JBColor.lazy { UIUtil.getListForeground() }
    }

    internal fun getTextColorSecondary(isSelected: Boolean = false): Color = when {
        isSelected -> getTextColorPrimary(isSelected)
        else -> GRAY_COLOR
    }

    internal fun setHeight(component: JComponent, height: Int, keepWidth: Boolean = false, scale: Boolean = true) {
        val scaledHeight = if (scale) JBUI.scale(height) else height

        component.apply {
            preferredSize = Dimension(if (keepWidth) preferredSize.width else 0, scaledHeight)
            minimumSize = Dimension(if (keepWidth) minimumSize.width else 0, scaledHeight)
            maximumSize = Dimension(if (keepWidth) maximumSize.width else Int.MAX_VALUE, scaledHeight)
        }
    }

    internal fun verticalScrollPane(c: Component) = object : JScrollPane(
        VerticalScrollPanelWrapper(c),
        VERTICAL_SCROLLBAR_AS_NEEDED,
        HORIZONTAL_SCROLLBAR_NEVER
    ) {
        init {
            border = BorderFactory.createEmptyBorder()
            viewport.background = UsualBackgroundColor
        }
    }

    internal fun overrideKeyStroke(c: JComponent, stroke: String, action: () -> Unit) = overrideKeyStroke(c, stroke, stroke, action)

    internal fun overrideKeyStroke(c: JComponent, key: String, stroke: String, action: () -> Unit) {
        val inputMap = c.getInputMap(JComponent.WHEN_FOCUSED)
        inputMap.put(KeyStroke.getKeyStroke(stroke), key)
        c.actionMap.put(
            key,
            object : AbstractAction() {
                override fun actionPerformed(arg: ActionEvent) {
                    action()
                }
            }
        )
    }

    private class VerticalScrollPanelWrapper(content: Component) : JPanel(), Scrollable {
        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(content)
        }

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 10
        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 100
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
        override fun getBackground() = UsualBackgroundColor
    }
}

internal class ComponentActionWrapper(private val myComponentCreator: () -> JComponent) : DumbAwareAction(), CustomComponentAction {

    override fun createCustomComponent(presentation: Presentation, place: String) = myComponentCreator()
    override fun actionPerformed(e: AnActionEvent) {
        // No-op
    }
}

internal fun JComponent.updateAndRepaint() {
    invalidate()
    repaint()
}
