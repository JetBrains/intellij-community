// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Placeholder
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.components.ValidationType
import com.intellij.ui.dsl.builder.components.validationTooltip
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.jetbrains.python.PyBundle.message
import java.nio.file.Path
import javax.swing.JComponent

internal sealed class VenvExistenceValidationState {
  data object Invisible : VenvExistenceValidationState()

  data class Warning(
    val venvPath: Path,
  ) : VenvExistenceValidationState()

  data class Error(
    val venvPath: Path,
  ) : VenvExistenceValidationState()
}

internal fun Row.venvExistenceValidationAlert(
  observableState: ObservableMutableProperty<VenvExistenceValidationState>,
  onSelectExisting: () -> Unit,
): Cell<JComponent> {
  lateinit var noticePlaceholder: Placeholder
  val isVisible = AtomicBooleanProperty(false)

  val rootCell = cell(com.intellij.ui.dsl.builder.panel {
    row {
      noticePlaceholder = placeholder().align(Align.FILL)

      val selectExitingEnvironment = ActionLink(message("sdk.create.custom.venv.select.existing.link")) {
        onSelectExisting()
      }

      observableState.afterChange { state ->
        isVisible.set(state !is VenvExistenceValidationState.Invisible)

        val actions = when (state) {
          VenvExistenceValidationState.Invisible -> listOf()
          is VenvExistenceValidationState.Warning -> listOfNotNull(selectExitingEnvironment)
          is VenvExistenceValidationState.Error -> listOfNotNull(
            ActionLink(message("sdk.create.custom.override.action")) {
              observableState.set(VenvExistenceValidationState.Warning(state.venvPath))
            },
            selectExitingEnvironment,
          )
        }
        val text = when (state) {
          VenvExistenceValidationState.Invisible -> ""
          is VenvExistenceValidationState.Warning -> message("sdk.create.custom.override.warning", state.venvPath.toString())
          is VenvExistenceValidationState.Error -> message("sdk.create.custom.override.error", state.venvPath.toString())
        }

        noticePlaceholder.component =
          if (state != VenvExistenceValidationState.Invisible)
            validationTooltip(
              message = text,
              firstActionLink = actions.getOrNull(0),
              secondActionLink = actions.getOrNull(1),
              validationType = if (state is VenvExistenceValidationState.Error) ValidationType.ERROR else ValidationType.WARNING
            )
              .component
          else
            null
      }
    }
  })
    .customize(UnscaledGaps(top = 4, bottom = 4))
    .visibleIf(isVisible)

  observableState.set(observableState.get()) // trigger state update to

  return rootCell
}