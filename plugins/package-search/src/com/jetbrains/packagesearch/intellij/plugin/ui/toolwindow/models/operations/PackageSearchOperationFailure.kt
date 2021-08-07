package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations

internal data class PackageSearchOperationFailure(
    val operation: PackageSearchOperation<*>,
    val exception: OperationException
)
