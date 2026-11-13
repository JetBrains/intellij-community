// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class MinimapEditorFactoryListener(private val scope: CoroutineScope) : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    if (editor.editorKind != EditorKind.MAIN_EDITOR || !MinimapAvailability.isAvailable()) return

    scope.launch {
      serviceAsync<MinimapSettings>()
      withContext(Dispatchers.EDT) {
        if (!editor.isDisposed && editor.project?.isDisposed != true) {
          MinimapService.getInstance().editorOpened(editor)
        }
      }
    }
  }
}
