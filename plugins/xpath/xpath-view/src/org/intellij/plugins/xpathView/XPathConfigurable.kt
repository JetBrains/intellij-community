// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.xpathView

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import java.awt.Color

internal class XPathConfigurable : BoundSearchableConfigurable(
  XPathBundle.message("configurable.XPathConfigurable.display.name"),
  "xpath.settings"
) {

  override fun createPanel(): DialogPanel {
    return panel {
      val config = XPathAppComponent.getInstance().getConfig()
      lateinit var useContextAtCursor: JBCheckBox

      group(XPathBundle.message("settings.settings")) {
        row {
          checkBox(XPathBundle.message("settings.scroll.first.hit.into.visible.area"))
            .bindSelected(config::scrollToFirst)
        }
        row {
          useContextAtCursor = checkBox(XPathBundle.message("settings.use.node.at.cursor.as.context.node"))
            .bindSelected(config::bUseContextAtCursor)
            .component
        }
        row {
          checkBox(XPathBundle.message("settings.highlight.only.start.tag.instead.of.whole.tag.content"))
            .bindSelected(config::bHighlightStartTagOnly)
        }
        row {
          checkBox(XPathBundle.message("settings.add.error.stripe.markers.for.each.result"))
            .bindSelected(config::bAddErrorStripe)
        }
      }

      group(XPathBundle.message("settings.colors")) {
        row(XPathBundle.message("settings.highlight.color")) {
          cell(ColorPanel())
            .bindColor({ config.attributes.backgroundColor }, { config.attributes.backgroundColor = it })
        }
        row(XPathBundle.message("settings.context.node.color")) {
          cell(ColorPanel())
            .bindColor({ config.contextAttributes.backgroundColor }, { config.contextAttributes.backgroundColor = it })
            .enabledIf(useContextAtCursor.selected)
            .applyIfEnabled()
        }
      }
    }
  }
}

private fun <T : ColorPanel> Cell<T>.bindColor(getter: () -> Color?, setter: (value: Color?) -> Unit): Cell<T> {
  bind({ it.selectedColor }, { it, value -> it.selectedColor = value }, MutableProperty(getter, setter))

  return this
}
