/*******************************************************************************
 * Copyright 2000-2023 JetBrains s.r.o. and contributors.
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

import com.intellij.ui.components.JBLabel
import com.intellij.ui.hover.TableHoverListener
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTableItem
import net.miginfocom.swing.MigLayout
import org.jetbrains.idea.packagesearch.SortMetric
import java.text.DecimalFormat
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

internal object PackageScoreTableCellRenderer : TableCellRenderer {

    private var sortMetric = SortMetric.NONE

    fun updateData(sortMetric: SortMetric) {
        this.sortMetric = sortMetric
    }

    private val suffix = arrayOf("", "k", "m", "b", "t")
    private const val MAX_LENGTH = 4
    private fun formatScore(score: Number): String {
        var r: String = DecimalFormat("##0E0").format(score)
        r = r.replace("E[0-9]".toRegex(), suffix[Character.getNumericValue(r[r.length - 1]) / 3])
        while (r.length > MAX_LENGTH || r.matches("[0-9]+\\.[a-z]".toRegex())) {
            r = r.substring(0, r.length - 2) + r.substring(r.length - 1)
        }
        return r
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ) = JPanel(MigLayout("al left center, insets 0 8 0 0")).apply {
        require(value is PackagesTableItem<*>) { "The value $value is expected to be a PackagesTableItem, but wasn't." }

        val sortMetricValue = value.packageModel.getSortMetricValue(sortMetric)
        val formattedMetricValue = formatScore(sortMetricValue)

        val scoreString: String = when (sortMetric) {
            SortMetric.GITHUB_STARS -> "$formattedMetricValue ★"
            SortMetric.OSS_HEALTH,
            SortMetric.DEPENDENCY_RATING,
            SortMetric.STACKOVERFLOW_HEALTH -> formattedMetricValue
            else -> ""
        }

        val isHover = TableHoverListener.getHoveredRow(table) == row
        val isSearchResult = value is PackagesTableItem.InstallablePackage

        val colors = computeColors(isSelected, isHover, isSearchResult)
        colors.applyTo(this)

        val labelComponent: JComponent =
            JBLabel().apply {
                text = scoreString
                background = colors.background
                foreground = colors.foreground
            }

        add(labelComponent)
    }
}
