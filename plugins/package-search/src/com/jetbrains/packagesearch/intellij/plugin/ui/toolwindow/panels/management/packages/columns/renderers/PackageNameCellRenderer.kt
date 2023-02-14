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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.hover.TableHoverListener
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.normalizeWhitespace
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTableItem
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.TagComponent
import com.jetbrains.packagesearch.intellij.plugin.ui.util.insets
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import net.miginfocom.layout.AC
import net.miginfocom.layout.BoundSize
import net.miginfocom.layout.CC
import net.miginfocom.layout.DimConstraint
import net.miginfocom.layout.LC
import net.miginfocom.layout.UnitValue
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

@Suppress("MagicNumber") // Swing dimension constants
internal object PackageNameCellRenderer : TableCellRenderer {

    private val layoutConstraints = LC().align("left", "center")
        .insets(left = 8, right = 0)

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
        val isHover = TableHoverListener.getHoveredRow(table) == row

        return when (value as PackagesTableItem<*>) {
            is PackagesTableItem.InstalledPackage -> {
                val packageModel = value.packageModel

                val name: String? = packageModel.remoteInfo?.name.normalizeWhitespace()
                val rawIdentifier = packageModel.identifier.rawValue

                val colors = computeColors(isSelected, isHover, isSearchResult = false)
                val additionalColors = AdditionalCellColors(
                    secondaryForeground = PackageSearchUI.getTextColorSecondary(isSelected),
                    tagBackground = PackageSearchUI.Colors.PackagesTable.Tag.background(isSelected, isHover),
                    tagForeground = PackageSearchUI.Colors.PackagesTable.Tag.foreground(isSelected, isHover),
                )

                createNamePanel(
                    columnWidth = columnWidth,
                    name = name,
                    identifier = rawIdentifier,
                    isKotlinMultiplatform = packageModel.isKotlinMultiplatform,
                    colors = colors,
                    additionalColors = additionalColors
                )
            }
            is PackagesTableItem.InstallablePackage -> {
                val packageModel = value.packageModel

                val name: String? = packageModel.remoteInfo?.name.normalizeWhitespace()
                val rawIdentifier = packageModel.identifier.rawValue

                val colors = computeColors(isSelected, isHover, isSearchResult = true)
                val additionalColors = AdditionalCellColors(
                    secondaryForeground = PackageSearchUI.getTextColorSecondary(isSelected),
                    tagBackground = PackageSearchUI.Colors.PackagesTable.SearchResult.Tag.background(isSelected, isHover),
                    tagForeground = PackageSearchUI.Colors.PackagesTable.SearchResult.Tag.foreground(isSelected, isHover),
                )

                createNamePanel(
                    columnWidth = columnWidth,
                    name = name,
                    identifier = rawIdentifier,
                    isKotlinMultiplatform = packageModel.isKotlinMultiplatform,
                    colors = colors,
                    additionalColors = additionalColors
                )
            }
        }
    }

    private fun createNamePanel(
        columnWidth: Int,
        @NlsSafe name: String?,
        @NlsSafe identifier: String,
        isKotlinMultiplatform: Boolean,
        colors: CellColors,
        additionalColors: AdditionalCellColors
    ) = TagPaintingJPanel(columnWidth).apply {
        colors.applyTo(this)

        if (!name.isNullOrBlank() && name !in identifier) {
            add(
                JLabel(name).apply {
                    colors.applyTo(this)
                },
                componentConstraint(gapAfter = componentGapX)
            )
            add(
                JLabel(identifier).apply {
                    colors.applyTo(this)
                },
                componentConstraint().gapAfter("0:push")
            )
        } else {
            add(
                JLabel(identifier).apply {
                    colors.applyTo(this)
                },
                componentConstraint()
            )
        }

        if (isKotlinMultiplatform) {
            val tagComponent = TagComponent(PackageSearchBundle.message("packagesearch.terminology.kotlinMultiplatform"))
                .apply { isVisible = false }

            add(tagComponent, componentConstraint(1, 0))

            tagComponent.background = additionalColors.tagBackground
            tagComponent.foreground = additionalColors.tagForeground
        }
    }

    // This is a hack; ideally we should have this done by the layout itself,
    // but MigLayout wasn't cooperating
    // TODO Use a custom layout to do this in a less hacky fashion
    private class TagPaintingJPanel(private val columnWidth: Int) : JPanel(
        MigLayout(layoutConstraints.width("${columnWidth}px!"), columnConstraints)
    ) {

        init {
            size = Dimension(columnWidth, height)
            maximumSize = Dimension(columnWidth, Int.MAX_VALUE)
        }

        override fun paint(g: Graphics) {
            super.paint(g)

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
                    paint(g)
                }
            }
        }
    }

    private data class AdditionalCellColors(
        val secondaryForeground: Color,
        val tagBackground: Color,
        val tagForeground: Color,
    )
}
