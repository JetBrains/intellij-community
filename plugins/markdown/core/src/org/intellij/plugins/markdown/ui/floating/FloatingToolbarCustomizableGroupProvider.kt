package org.intellij.plugins.markdown.ui.floating

import com.intellij.ide.ui.customization.CustomizableActionGroupProvider
import org.intellij.plugins.markdown.MarkdownBundle

internal class FloatingToolbarCustomizableGroupProvider: CustomizableActionGroupProvider() {
  override fun registerGroups(registrar: CustomizableActionGroupRegistrar) {
    registrar.addCustomizableActionGroup(
      "Markdown.Toolbar.Floating",
      MarkdownBundle.message("markdown.floating.toolbar.customizable.group.name")
    )
  }
}
