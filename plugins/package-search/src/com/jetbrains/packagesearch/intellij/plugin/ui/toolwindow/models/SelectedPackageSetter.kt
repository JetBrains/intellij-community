package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

internal interface SelectedPackageSetter {

    fun setSelectedPackage(selectedPackageModel: SelectedPackageModel<*>?)
}
