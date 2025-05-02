// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.uv.run.UvRunConfigurationOptions

internal fun showSyncWarningDialogAndGet(isError: Boolean, options: UvRunConfigurationOptions): Boolean {
  return DialogBuilder()
    .title(PyBundle.message("uv.run.configuration.state.sync.warning.title"))
    .apply {
      lateinit var dontAskAgainCheckbox: JBCheckBox

      setCenterPanel(
        panel {
          row {
            label(
              if (isError) {
                PyBundle.message("uv.run.configuration.state.sync.warning.body.will")
              }
              else {
                PyBundle.message("uv.run.configuration.state.sync.warning.body.might")
              }
            )
          }

          row {
            dontAskAgainCheckbox = checkBox(PyBundle.message("uv.run.configuration.state.sync.warning.dont.ask.again"))
              .component
          }
        }
      )
      setOkOperation {
        options.checkSync = !dontAskAgainCheckbox.isSelected
        dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
      }
    }
    .showAndGet()
}
