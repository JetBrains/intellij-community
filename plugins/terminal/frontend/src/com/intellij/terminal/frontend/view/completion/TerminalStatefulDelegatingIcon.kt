package com.intellij.terminal.frontend.view.completion

import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

/**
 * Delegates icon to [baseIcon] but allows to force it to be another icon.
 *
 * It is a kind of hack for the terminal command completion case.
 * We use it in [com.intellij.codeInsight.lookup.LookupElementPresentation] to make it possible to change the icon later.
 * Since the completion API doesn't allow changing the presentation after the element is added to the Lookup.
 */
internal class TerminalStatefulDelegatingIcon(private val baseIcon: Icon) : Icon {
  private var forcedIcon: Icon? = null

  private val actualIcon: Icon
    get() = forcedIcon ?: baseIcon

  fun forceIcon(icon: Icon) {
    forcedIcon = icon
  }

  fun useDefaultIcon() {
    forcedIcon = null
  }

  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
    actualIcon.paintIcon(c, g, x, y)
  }

  override fun getIconWidth(): Int {
    return actualIcon.iconWidth
  }

  override fun getIconHeight(): Int {
    return actualIcon.iconHeight
  }
}