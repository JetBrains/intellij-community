// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus

/** Bridge between the backend highlighting pass and the frontend reconciler. */
@ApiStatus.Internal
interface MarkdownLivePreviewReconcilerBridge {
  fun hasExistingReconciler(editor: Editor): Boolean
  fun publish(editor: Editor, specSet: MarkdownConcealSpecSet): Boolean

  companion object {
    fun getInstance(): MarkdownLivePreviewReconcilerBridge? =
      ApplicationManager.getApplication().getService(MarkdownLivePreviewReconcilerBridge::class.java)
  }
}
