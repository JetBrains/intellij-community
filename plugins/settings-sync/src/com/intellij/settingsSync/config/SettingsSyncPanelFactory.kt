package com.intellij.settingsSync.config

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*
import org.jetbrains.annotations.Nls

object SettingsSyncPanelFactory {
  fun createPanel(syncLabel: @Nls String): DialogPanel {
    return panel {
      row {
        label(syncLabel)
      }
      row {
        SettingsCategoryDescriptor.listAll().forEach { descriptor ->
          row {
            cell {
              checkBox(
                descriptor.name,
                { descriptor.isSynchronized },
                { descriptor.isSynchronized = it }
              )
                .onReset { descriptor.reset() }
                .onApply { descriptor.apply() }
                .onIsModified { descriptor.isModified() }
              commentNoWrap(descriptor.description)
            }
          }
        }
      }
    }
  }
}