// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.TerminalTitleListener
import com.intellij.ui.content.Content
import com.intellij.util.text.UniqueNameGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.buildSettingsAwareTitle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
object TerminalTitleUtils {
  @JvmStatic
  fun createDefaultTabName(
    toolWindow: ToolWindow,
    defaultName: String = TerminalOptionsProvider.instance.tabName,
  ): @NlsSafe String {
    val existingNames = toolWindow.contentManager.contentsRecursively.map { it.displayName }
    return UniqueNameGenerator.generateUniqueName(
      defaultName,
      "",
      "",
      " (",
      ")",
      Condition { !existingNames.contains(it) }
    )
  }

  @JvmStatic
  @OptIn(FlowPreview::class)
  fun updateTabNameOnTitleChange(title: TerminalTitle, content: Content, scope: CoroutineScope) {
    scope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      title.stateFlow { it.buildSettingsAwareTitle() }
        .debounce(TITLE_UPDATE_DELAY)
        .collect {
          content.displayName = it.text
        }
    }
  }

  @JvmStatic
  @OptIn(FlowPreview::class)
  fun updateFileNameOnTitleChange(
    title: TerminalTitle,
    file: VirtualFile,
    project: Project,
    scope: CoroutineScope,
  ) {
    scope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      title.stateFlow { it.buildSettingsAwareTitle() }
        .debounce(TITLE_UPDATE_DELAY)
        .collect {
          file.rename(null, it.text)
          FileEditorManager.getInstance(project).updateFilePresentation(file)
        }
    }
  }

  fun TerminalTitle.stateFlow(buildTitle: (TerminalTitle) -> String): Flow<TitleData> {
    fun titleData(title: TerminalTitle): TitleData {
      return TitleData(buildTitle(title), title.defaultTitle, title.userDefinedTitle)
    }

    val flow = channelFlow {
      val disposable = Disposer.newDisposable()
      addTitleListener(object : TerminalTitleListener {
        override fun onTitleChanged(terminalTitle: TerminalTitle) {
          trySend(titleData(terminalTitle))
        }
      }, disposable)

      send(titleData(this@stateFlow))

      awaitClose { Disposer.dispose(disposable) }
    }

    return flow.distinctUntilChanged()
  }

  val TITLE_UPDATE_DELAY: Duration = 300.milliseconds

  data class TitleData(
    @param:NlsSafe val text: String,
    val defaultName: String?,
    val userDefinedName: String?,
  )
}