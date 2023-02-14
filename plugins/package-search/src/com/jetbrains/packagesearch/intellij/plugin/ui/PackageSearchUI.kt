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

package com.jetbrains.packagesearch.intellij.plugin.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.*
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.packagesearch.intellij.plugin.ui.components.BrowsableLinkLabel
import com.jetbrains.packagesearch.intellij.plugin.ui.util.ScalableUnits
import com.jetbrains.packagesearch.intellij.plugin.ui.util.ScaledPixels
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*

object PackageSearchUI {

    internal object Colors {

        val border = JBColor.border()
        val separator = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()

        private val mainBackground: Color = JBColor.namedColor("Plugins.background", UIUtil.getListBackground())

        val infoLabelForeground: Color = JBColor.namedColor("Label.infoForeground", JBColor(Gray._120, Gray._135))

        val headerBackground = mainBackground
        val sectionHeaderBackground = JBColor.namedColor("Plugins.SectionHeader.background", 0xF7F7F7, 0x3C3F41)

        val panelBackground
            get() = if (isNewUI) JBColor.namedColor("Panel.background") else mainBackground

        interface StatefulColor {

            fun background(isSelected: Boolean, isHover: Boolean): Color
            fun foreground(isSelected: Boolean, isHover: Boolean): Color
        }

        object PackagesTable : StatefulColor {

            override fun background(isSelected: Boolean, isHover: Boolean) =
                when {
                    isSelected -> tableSelectedBackground
                    isHover -> tableHoverBackground
                    else -> tableBackground
                }

            override fun foreground(isSelected: Boolean, isHover: Boolean) =
                when {
                    isSelected -> tableSelectedForeground
                    else -> tableForeground
                }

            private val tableBackground = JBColor.namedColor("Table.background")
            private val tableForeground = JBColor.namedColor("Table.foreground")

            private val tableHoverBackground
                get() = color(
                    propertyName = "PackageSearch.SearchResult.hoverBackground",
                    newUILight = 0x2C3341, newUIDark = 0xDBE1EC,
                    oldUILight = 0xF2F5F9, oldUIDark = 0x4C5052
                )

            private val tableSelectedBackground = JBColor.namedColor("Table.selectionBackground")
            private val tableSelectedForeground = JBColor.namedColor("Table.selectionForeground")

            object SearchResult : StatefulColor {

                override fun background(isSelected: Boolean, isHover: Boolean) =
                    when {
                        isSelected -> searchResultSelectedBackground
                        isHover -> searchResultHoverBackground
                        else -> searchResultBackground
                    }

                override fun foreground(isSelected: Boolean, isHover: Boolean) =
                    when {
                        isSelected -> searchResultSelectedForeground
                        else -> searchResultForeground
                    }

                private val searchResultBackground
                    get() = color(
                        propertyName = "PackageSearch.SearchResult.background",
                        newUILight = 0xE8EEFA, newUIDark = 0x1C2433,
                        oldUILight = 0xE4FAFF, oldUIDark = 0x3F4749
                    )

                private val searchResultForeground = tableForeground

                private val searchResultSelectedBackground = tableSelectedBackground
                private val searchResultSelectedForeground = tableSelectedForeground

                private val searchResultHoverBackground
                    get() = color(
                        propertyName = "PackageSearch.SearchResult.hoverBackground",
                        newUILight = 0xDBE1EC, newUIDark = 0x2C3341,
                        oldUILight = 0xF2F5F9, oldUIDark = 0x4C5052
                    )

                object Tag : StatefulColor {

                    override fun background(isSelected: Boolean, isHover: Boolean) =
                        when {
                            isSelected -> searchResultTagSelectedBackground
                            isHover -> searchResultTagHoverBackground
                            else -> searchResultTagBackground
                        }

                    override fun foreground(isSelected: Boolean, isHover: Boolean) =
                        when {
                            isSelected -> searchResultTagSelectedForeground
                            else -> searchResultTagForeground
                        }

                    private val searchResultTagBackground
                        get() = color(
                            propertyName = "PackageSearch.SearchResult.PackageTag.background",
                            newUILight = 0xD5DBE6, newUIDark = 0x2E3643,
                            oldUILight = 0xD2E6EB, oldUIDark = 0x4E5658
                        )

                    private val searchResultTagForeground
                        get() = color(
                            propertyName = "PackageSearch.SearchResult.PackageTag.foreground",
                            newUILight = 0x000000, newUIDark = 0xDFE1E5,
                            oldUILight = 0x000000, oldUIDark = 0x8E8F8F
                        )

                    private val searchResultTagHoverBackground
                        get() = color(
                            propertyName = "PackageSearch.SearchResult.PackageTag.hoverBackground",
                            newUILight = 0xC9CFD9, newUIDark = 0x3D4350,
                            oldUILight = 0xBFD0DB, oldUIDark = 0x55585B
                        )

                    private val searchResultTagSelectedBackground
                        get() = color(
                            propertyName = "PackageSearch.SearchResult.PackageTag.selectedBackground",
                            newUILight = 0xA0BDF8, newUIDark = 0x375FAD,
                            oldUILight = 0x4395E2, oldUIDark = 0x2F65CA
                        )

                    private val searchResultTagSelectedForeground
                        get() = color(
                            propertyName = "PackageSearch.SearchResult.PackageTag.selectedForeground",
                            newUILight = 0x000000, newUIDark = 0x1E1F22,
                            oldUILight = 0xFFFFFF, oldUIDark = 0xBBBBBB
                        )
                }
            }

            object Tag : StatefulColor {

                override fun background(isSelected: Boolean, isHover: Boolean) =
                    when {
                        isSelected -> tagSelectedBackground
                        isHover -> tagHoverBackground
                        else -> tagBackground
                    }

                override fun foreground(isSelected: Boolean, isHover: Boolean) =
                    when {
                        isSelected -> tagSelectedForeground
                        else -> tagForeground
                    }

                private val tagBackground
                    get() = color(
                        propertyName = "PackageSearch.PackageTag.background",
                        newUILight = 0xDFE1E5, newUIDark = 0x43454A,
                        oldUILight = 0xEBEBEB, oldUIDark = 0x4C4E50
                    )

                private val tagForeground
                    get() = color(
                        propertyName = "PackageSearch.PackageTag.foreground",
                        newUILight = 0x5A5D6B, newUIDark = 0x9DA0A8,
                        oldUILight = 0x000000, oldUIDark = 0x9C9C9C
                    )

                private val tagHoverBackground
                    get() = color(
                        propertyName = "PackageSearch.PackageTag.hoverBackground",
                        newUILight = 0xF7F8FA, newUIDark = 0x4A4C4E,
                        oldUILight = 0xC9D2DB, oldUIDark = 0x55585B
                    )

                private val tagSelectedBackground
                    get() = color(
                        propertyName = "PackageSearch.PackageTag.selectedBackground",
                        newUILight = 0x88ADF7, newUIDark = 0x43454A,
                        oldUILight = 0x4395E2, oldUIDark = 0x2F65CA
                    )

                private val tagSelectedForeground
                    get() = color(
                        propertyName = "PackageSearch.PackageTag.selectedForeground",
                        newUILight = 0x000000, newUIDark = 0x9DA0A8,
                        oldUILight = 0xFFFFFF, oldUIDark = 0xBBBBBB
                    )
            }
        }

        object InfoBanner {

            val background = JBUI.CurrentTheme.Banner.INFO_BACKGROUND

            val border = JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR
        }

        private fun color(
            propertyName: String? = null,
            newUILight: Int,
            newUIDark: Int,
            oldUILight: Int,
            oldUIDark: Int
        ) =
            if (propertyName != null) {
                JBColor.namedColor(
                    propertyName,
                    if (isNewUI) newUILight else oldUILight,
                    if (isNewUI) newUIDark else oldUIDark
                )
            } else {
                JBColor(
                    if (isNewUI) newUILight else oldUILight,
                    if (isNewUI) newUIDark else oldUIDark
                )
            }
    }

    internal val mediumHeaderHeight = JBValue.Float(30f)
    internal val smallHeaderHeight = JBValue.Float(24f)

    val isNewUI
        get() = ExperimentalUI.isNewUI()

    @Suppress("MagicNumber") // Thanks, Swing
    internal fun headerPanel(init: BorderLayoutPanel.() -> Unit) = object : BorderLayoutPanel() {
        init {
            border = JBEmptyBorder(2, 0, 2, 12)
            init()
        }

        override fun getBackground() = Colors.headerBackground
    }

    internal fun cardPanel(cards: List<JPanel> = emptyList(), backgroundColor: Color = Colors.panelBackground, init: JPanel.() -> Unit) =
        object : JPanel() {
            init {
                layout = CardLayout()
                cards.forEach { add(it) }
                init()
            }

            override fun getBackground() = backgroundColor
        }

    internal fun borderPanel(backgroundColor: Color = Colors.panelBackground, init: BorderLayoutPanel.() -> Unit) =
        object : BorderLayoutPanel() {

            init {
                init()
            }

            override fun getBackground() = backgroundColor
        }

    internal fun boxPanel(axis: Int = BoxLayout.Y_AXIS, backgroundColor: Color = Colors.panelBackground, init: JPanel.() -> Unit) =
        object : JPanel() {
            init {
                layout = BoxLayout(this, axis)
                init()
            }

            override fun getBackground() = backgroundColor
        }

    internal fun flowPanel(backgroundColor: Color = Colors.panelBackground, init: JPanel.() -> Unit) = object : JPanel() {
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

        override fun getBackground() = Colors.panelBackground
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

    private fun getTextColorPrimary(isSelected: Boolean = false): Color = when {
        isSelected -> JBColor.lazy { NamedColorUtil.getListSelectionForeground(true) }
        else -> JBColor.lazy { UIUtil.getListForeground() }
    }

    internal fun getTextColorSecondary(isSelected: Boolean = false): Color = when {
        isSelected -> getTextColorPrimary(true)
        else -> Colors.infoLabelForeground
    }

    internal fun setHeight(component: JComponent, @ScalableUnits height: Int, keepWidth: Boolean = false) {
        setHeightPreScaled(component, height.scaled(), keepWidth)
    }

    internal fun setHeightPreScaled(component: JComponent, @ScaledPixels height: Int, keepWidth: Boolean = false) {
        component.apply {
            preferredSize = Dimension(if (keepWidth) preferredSize.width else 0, height)
            minimumSize = Dimension(if (keepWidth) minimumSize.width else 0, height)
            maximumSize = Dimension(if (keepWidth) maximumSize.width else Int.MAX_VALUE, height)
        }
    }

    internal fun verticalScrollPane(c: Component) = object : JScrollPane(
        VerticalScrollPanelWrapper(c),
        VERTICAL_SCROLLBAR_AS_NEEDED,
        HORIZONTAL_SCROLLBAR_NEVER
    ) {
        init {
            border = BorderFactory.createEmptyBorder()
            viewport.background = Colors.panelBackground
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
        override fun getBackground() = Colors.panelBackground
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
