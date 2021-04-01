package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug

internal class PackagesTableModel(
    var onlyStable: Boolean,
    vararg val columns: ColumnInfo<PackagesTableItem<*>, *>
) : ListTableModel<PackagesTableItem<*>>(*columns) {

    override fun getRowCount() = items.size
    override fun getColumnCount() = columns.size
    override fun getColumnClass(columnIndex: Int): Class<out Any> = columns[columnIndex].javaClass

    fun replaceItemMatching(packageModel: PackageModel, itemProducer: (PackagesTableItem<*>) -> PackagesTableItem<*>) {
        val newItems = mutableListOf<PackagesTableItem<*>>()
        newItems.addAll(items.takeWhile { it.packageModel != packageModel })
        if (newItems.size == items.size) {
            logDebug("PackagesTableModel#replaceItem()") { "Could not replace with model ${packageModel.identifier}: not found" }
            return
        }
        newItems.add(itemProducer(items[newItems.size]))
        newItems.addAll(items.subList(newItems.size, items.size - 1))
        items = newItems
    }
}
