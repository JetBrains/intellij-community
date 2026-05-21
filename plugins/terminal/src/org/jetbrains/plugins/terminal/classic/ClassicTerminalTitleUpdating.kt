// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.classic

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.flow.throttleLatest
import com.intellij.terminal.TerminalTitle
import com.intellij.ui.content.Content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.TITLE_UPDATE_DELAY
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareFullTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.stateFlow

internal fun updateTabNameOnTitleChange(
  title: TerminalTitle,
  content: Content,
  scope: CoroutineScope,
) {
  scope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
    classicTerminalTitleStateFlow(title)
      .throttleLatest(TITLE_UPDATE_DELAY)
      .collect {
        content.displayName = it.croppedText
        content.description = StringUtil.escapeXmlEntities(it.fullText)
      }
  }
}

internal fun updateFileNameOnTitleChange(
  title: TerminalTitle,
  file: VirtualFile,
  project: Project,
  scope: CoroutineScope,
) {
  scope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
    classicTerminalTitleStateFlow(title)
      .throttleLatest(TITLE_UPDATE_DELAY)
      .collect {
        file.rename(null, it.croppedText)
        FileEditorManager.getInstance(project).updateFilePresentation(file)
      }
  }
}

private fun classicTerminalTitleStateFlow(title: TerminalTitle): Flow<TerminalTitleUtils.TitleData> {
  return title.stateFlow(
    buildCroppedTitle = { it.buildSettingsAwareTitle() },
    buildFullTitle = { it.buildSettingsAwareFullTitle() }
  )
}