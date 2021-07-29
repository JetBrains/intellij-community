package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.UiPackageModel

internal data class UiPackageModelWithNewValue<T>(
    val uiPackageModel: UiPackageModel<*>,
    val newValue: T?
)

internal fun <T> UiPackageModel<*>.toUiPackageModelWithNewValue(): UiPackageModelWithNewValue<T?> =
    UiPackageModelWithNewValue(this, newValue = null as T?)
