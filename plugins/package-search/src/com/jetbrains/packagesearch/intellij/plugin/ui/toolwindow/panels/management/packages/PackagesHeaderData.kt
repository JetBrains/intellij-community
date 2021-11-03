package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.Nls

internal data class PackagesHeaderData(
    @Nls val labelText: String,
    val count: Int,
    val availableUpdatesCount: Int,
    val updateOperations: Deferred<List<PackageSearchOperation<*>>>
) {

//    companion object {
//
//        val EMPTY = PackagesHeaderData("", 0, 0, Deferred(emptyList()))
//    }
}
