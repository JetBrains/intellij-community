package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener

@Suppress("FunctionName")
internal fun SelectionChangedListener(action: (ContentManagerEvent) -> Unit) = object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) = action(event)
}

internal fun ContentManager.addSelectionChangedListener(action: (ContentManagerEvent) -> Unit) =
    SelectionChangedListener(action).also { addContentManagerListener(it) }
