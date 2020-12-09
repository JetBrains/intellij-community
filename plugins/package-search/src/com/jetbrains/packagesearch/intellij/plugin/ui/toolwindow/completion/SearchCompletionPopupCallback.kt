package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.completion

import com.intellij.util.Consumer

abstract class SearchCompletionPopupCallback(var prefix: String?) : Consumer<String>
