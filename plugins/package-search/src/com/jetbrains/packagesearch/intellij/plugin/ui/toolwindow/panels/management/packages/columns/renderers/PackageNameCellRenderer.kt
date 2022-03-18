package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.normalizeWhitespace
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTableItem
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.TagComponent
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.colors
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaledInsets
import net.miginfocom.layout.AC
import net.miginfocom.layout.BoundSize
import net.miginfocom.layout.CC
import net.miginfocom.layout.DimConstraint
import net.miginfocom.layout.LC
import net.miginfocom.layout.UnitValue
import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

@Suppress("MagicNumber") // Swing dimension constants
internal object PackageNameCellRenderer : TableCellRenderer {

    private val layoutConstraints = LC().align("left", "center")
        .scaledInsets(left = 8, right = 0)

    private val componentGapX = 4.scaled()

    private val columnConstraints = AC().apply {
        gap(componentGapX.toString())
        constaints = arrayOf(
            DimConstraint().apply {
                gap(componentGapX.toString())
                size = BoundSize(UnitValue(150F, UnitValue.PIXEL, ""), "")
            },
            DimConstraint().apply {
                gapBefore = BoundSize(UnitValue(componentGapX / 2F), "")
            }
        )
    }

    private val tagForeground = JBColor(0x808080, 0x9C9C9C)
    private val tagBackground = JBColor(0xE5E5E5, 0x666B6E)
    private val tagForegroundSelected = JBColor(0xFFFFFF, 0xFFFFFF)
    private val tagBackgroundSelected = JBColor(0x4395E2, 0x78ADE2)

    private fun componentConstraint(x: Int = 0, y: Int = 0, gapAfter: Int? = null): CC = CC().apply {
        cellX = x
        cellY = y
        if (gapAfter != null) gapAfter(gapAfter.toString())
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): JPanel {
        val columnWidth = table.tableHeader.columnModel.getColumn(0).width

        return when (value as PackagesTableItem<*>) {
            is PackagesTableItem.InstalledPackage -> {
                val packageModel = value.packageModel

                val name: String? = packageModel.remoteInfo?.name.normalizeWhitespace()
                val rawIdentifier = packageModel.identifier.rawValue

                createNamePanel(columnWidth, name, rawIdentifier, packageModel.isKotlinMultiplatform, isSelected).apply {
                    table.colors.applyTo(this, isSelected)
                }
            }
            is PackagesTableItem.InstallablePackage -> {
                val packageModel = value.packageModel

                val name: String? = packageModel.remoteInfo?.name.normalizeWhitespace()
                val rawIdentifier = packageModel.identifier.rawValue

                createNamePanel(columnWidth, name, rawIdentifier, packageModel.isKotlinMultiplatform, isSelected).apply {
                    table.colors.applyTo(this, isSelected)
                    if (!isSelected) background = PackageSearchUI.ListRowHighlightBackground
                }
            }
        }
    }

    private fun createNamePanel(
        columnWidth: Int,
        @NlsSafe name: String?,
        @NlsSafe identifier: String,
        isKotlinMultiplatform: Boolean,
        isSelected: Boolean
    ) = TagPaintingJPanel(columnWidth, isSelected).apply {
        if (!name.isNullOrBlank() && name != identifier) {
            add(
                JLabel(name).apply {
                    foreground = PackageSearchUI.getTextColorPrimary(isSelected)
                },
                componentConstraint(gapAfter = componentGapX)
            )
            add(
                JLabel(identifier).apply {
                    foreground = PackageSearchUI.getTextColorSecondary(isSelected)
                },
                componentConstraint().gapAfter("0:push")
            )
        } else {
            add(
                JLabel(identifier).apply {
                    foreground = PackageSearchUI.getTextColorPrimary(isSelected)
                },
                componentConstraint()
            )
        }

        if (isKotlinMultiplatform) {
            add(
                TagComponent(PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform"))
                    .apply { isVisible = false },
                componentConstraint(1, 0)
            )
        }
    }

    // This is a hack; ideally we should have this done by the layout itself,
    // but MigLayout wasn't cooperating
    // TODO Use a custom layout to do this in a less hacky fashion
    private class TagPaintingJPanel(private val columnWidth: Int, private val isSelected: Boolean) : JPanel(
        MigLayout(layoutConstraints.width("${columnWidth}px!"), columnConstraints)
    ) {

        init {
            size = Dimension(columnWidth, height)
            maximumSize = Dimension(columnWidth, Int.MAX_VALUE)
        }

        override fun paintChildren(g: Graphics) {
            super.paintChildren(g)

            val tagComponent = components.find { it is TagComponent } as? TagComponent ?: return
            val tagX = columnWidth - tagComponent.width
            val tagY = height / 2 - tagComponent.height / 2

            g.apply {
                // We first paint over the gap between the text and the tag, to have a pretend margin if needed
                color = background
                fillRect(tagX - componentGapX, 0, columnWidth - tagX, height)

                // Then we manually translate the tag to the right-hand side of the row and paint it
                translate(tagX, tagY)
                tagComponent.apply {
                    isVisible = true
                    foreground = JBColor.namedColor("Plugins.tagForeground", if (isSelected) tagForegroundSelected else tagForeground)
                    background = JBColor.namedColor("Plugins.tagBackground", if (isSelected) tagBackgroundSelected else tagBackground)
                    paint(g)
                }
            }
        }
    }
}
