package com.jetbrains.packagesearch.intellij.plugin.ui.util

internal interface Displayable<T : Any> {
    suspend fun display(viewModel: T)
}
