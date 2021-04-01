package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation

internal data class PackagesHeaderData(
    val labelText: String,
    val count: Int?,
    val availableUpdatesCount: Int,
    val updateOperations: List<PackageSearchOperation<*>>
) {

    companion object {
        val EMPTY = PackagesHeaderData("", null, 0, emptyList())
    }
}
