// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui

import com.intellij.python.pytools.lsp.PyLspToolSettings
import com.intellij.python.pytools.ui.PyToolsUiBundle.message
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import kotlin.reflect.KMutableProperty0

fun KMutableProperty0<Boolean?>.toSafeProperty(default: Boolean = false): MutableProperty<Boolean> =
  MutableProperty({ get() ?: default }, { set(it) })

/**
 * Helper for building the per-tool feature row block (inspections / completions / inlay hints / documentation)
 * shown in each LSP-backed tool's detail dialog. The Enable / installation rows are now owned by the
 * `External Tools` table — this helper only renders feature toggles.
 */
object PyLspToolFeatureRows {
  fun build(
    panel: Panel,
    settings: PyLspToolSettings,
    inlayHintLabel: String = message("checkbox.inlay.hints"),
    extra: (Panel.() -> Unit)? = null,
  ) {
    with(panel) {
      row(message("label.features")) {
        checkBox(message("checkbox.inspections"))
          .bindSelected(settings::inspections)
      }
      if (settings.completions != null) {
        row("") {
          checkBox(message("checkbox.completions"))
            .bindSelected(settings::completions.toSafeProperty())
        }
      }
      if (settings.inlayHints != null) {
        row("") {
          checkBox(inlayHintLabel)
            .bindSelected(settings::inlayHints.toSafeProperty())
        }
      }
      if (settings.documentation != null) {
        row("") {
          checkBox(message("checkbox.documentation"))
            .bindSelected(settings::documentation.toSafeProperty())
        }
      }
      extra?.invoke(this)
    }
  }
}
