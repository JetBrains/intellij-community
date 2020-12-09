// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.actions

import com.intellij.configurationStore.StateStorageManagerImpl
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.settingsRepository.IcsBundle
import org.jetbrains.settingsRepository.createMergeActions
import org.jetbrains.settingsRepository.icsManager
import org.jetbrains.settingsRepository.icsMessage
import kotlin.properties.Delegates

internal class ConfigureIcsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    runBlocking {
      icsManager.runInAutoCommitDisabledMode {
        var urlTextField: TextFieldWithBrowseButton by Delegates.notNull()
        val panel = panel {
          row(icsMessage("settings.upstream.url")) {
            urlTextField = textFieldWithBrowseButton(value = icsManager.repositoryManager.getUpstream(),
                                                     browseDialogTitle = icsMessage("configure.ics.choose.local.repository.dialog.title"),
                                                     fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()).component
          }

          noteRow(IcsBundle.message("message.see.help.pages.for.more.info",
                                    "https://www.jetbrains.com/help/idea/sharing-your-ide-settings.html#settings-repository"))
        }
        dialog(title = icsMessage("settings.panel.title"),
               panel = panel,
               focusedComponent = urlTextField,
               project = e.project,
               createActions = {
                 createMergeActions(e.project, urlTextField, it)
               })
          .show()
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode) {
      return
    }

    e.presentation.isEnabledAndVisible = icsManager.isActive || !(application.stateStore.storageManager as StateStorageManagerImpl).compoundStreamProvider.isExclusivelyEnabled
    if (!e.presentation.isEnabledAndVisible && ActionPlaces.MAIN_MENU == e.place) {
      e.presentation.isVisible = true
    }
    e.presentation.icon = null
  }
}