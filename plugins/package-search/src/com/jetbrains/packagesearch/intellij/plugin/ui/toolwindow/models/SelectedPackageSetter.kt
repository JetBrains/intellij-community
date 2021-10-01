package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

internal interface SelectedPackageSetter {

    suspend fun setSelectedPackage(selectedPackageModel: SelectedPackageModel<*>?)
}
