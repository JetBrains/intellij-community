package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import org.jetbrains.annotations.Nls

internal data class PackagesHeaderData(
    @Nls val labelText: String,
    val count: Int?,
    val availableUpdatesCount: Int,
    val updateOperations: List<PackageSearchOperation<*>>
) {

    companion object {
        val EMPTY = PackagesHeaderData("", null, 0, emptyList())
    }
}
