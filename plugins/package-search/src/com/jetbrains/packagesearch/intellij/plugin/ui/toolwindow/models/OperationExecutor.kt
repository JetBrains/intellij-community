package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation
import kotlinx.coroutines.Deferred

internal interface OperationExecutor {

    fun executeOperations(operations: List<PackageSearchOperation<*>>)

    fun executeOperations(operations: Deferred<List<PackageSearchOperation<*>>>)
}
