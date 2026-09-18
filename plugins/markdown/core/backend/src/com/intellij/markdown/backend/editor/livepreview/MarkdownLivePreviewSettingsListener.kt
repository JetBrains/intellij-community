// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.editor.livepreview

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.project.getOpenedProjects
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSettingListener

internal class MarkdownLivePreviewSettingsListener : MarkdownLivePreviewSettingListener {
  override fun livePreviewSettingChanged() {
    for (project in getOpenedProjects()) {
      DaemonCodeAnalyzer.getInstance(project).restart("Markdown application settings changed")
    }
  }
}
