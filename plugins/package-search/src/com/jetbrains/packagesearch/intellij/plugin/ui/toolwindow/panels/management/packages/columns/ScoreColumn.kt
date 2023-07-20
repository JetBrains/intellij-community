package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns

import com.intellij.util.ui.ColumnInfo
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.PackagesTableItem
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers.PackageScoreTableCellRenderer
import org.jetbrains.idea.packagesearch.SortMetric
import javax.swing.table.TableCellRenderer

internal class ScoreColumn : ColumnInfo<PackagesTableItem<*>, PackagesTableItem<*>>(
    PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.score")
) {
    private val cellRenderer = PackageScoreTableCellRenderer

    private var sortMetric: SortMetric = SortMetric.NONE

    override fun getRenderer(item: PackagesTableItem<*>): TableCellRenderer = cellRenderer

    fun updateData(sortMetric: SortMetric) {
        this.sortMetric = sortMetric
        cellRenderer.updateData(sortMetric)
    }

    override fun valueOf(item: PackagesTableItem<*>): PackagesTableItem<*> = item
}