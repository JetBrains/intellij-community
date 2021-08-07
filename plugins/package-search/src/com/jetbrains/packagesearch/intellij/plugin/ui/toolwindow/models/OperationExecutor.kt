package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperation

internal interface OperationExecutor {

    fun executeOperations(operations: List<PackageSearchOperation<*>>)
}
