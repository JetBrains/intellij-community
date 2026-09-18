// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic

interface MarkdownLivePreviewSettingListener {
  fun livePreviewSettingChanged()

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<MarkdownLivePreviewSettingListener> = Topic("MarkdownLivePreviewSettingChanged", MarkdownLivePreviewSettingListener::class.java)

    fun fireChanged() {
      ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).livePreviewSettingChanged()
    }
  }
}
