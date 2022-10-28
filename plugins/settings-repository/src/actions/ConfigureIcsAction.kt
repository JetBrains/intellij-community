// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository.actions

import com.intellij.configurationStore.StateStorageManagerImpl
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import org.jetbrains.settingsRepository.IcsBundle
import org.jetbrains.settingsRepository.createMergeActions
import org.jetbrains.settingsRepository.icsManager
import org.jetbrains.settingsRepository.icsMessage
import kotlin.properties.Delegates

internal class ConfigureIcsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val owner = e.project?.let(ModalTaskOwner::project)
                ?: e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)?.let(ModalTaskOwner::component)
                ?: ModalTaskOwner.guess()
    runBlockingModal(owner, "") { // TODO title
      icsManager.runInAutoCommitDisabledMode {
        var urlTextField: TextFieldWithBrowseButton by Delegates.notNull()
        val panel = panel {
          row(icsMessage("settings.upstream.url")) {
            urlTextField = textFieldWithBrowseButton(browseDialogTitle = icsMessage("configure.ics.choose.local.repository.dialog.title"),
                                                     fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor())
              .text(icsManager.repositoryManager.getUpstream() ?: "")
              .align(AlignX.FILL)
              .component
          }
          row {
            comment(IcsBundle.message("message.see.help.pages.for.more.info",
                                      "https://www.jetbrains.com/help/idea/sharing-your-ide-settings.html#settings-repository"))
          }
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

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

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