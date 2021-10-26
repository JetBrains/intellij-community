package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import kotlinx.coroutines.flow.StateFlow

interface UIStateModifier {

    val programmaticSearchQueryStateFlow: StateFlow<String>
}
