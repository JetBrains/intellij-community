package com.intellij.laf.macos

import com.intellij.ui.components.SearchFieldWithExtension
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

@Suppress("unused")
@ApiStatus.Experimental
class MacIntelliSearchBorder : MacIntelliJTextBorder() {
  override fun paintBorder(c: Component?, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    if (c !is SearchFieldWithExtension) return
    val r = Rectangle(c.size)
    paintMacSearchArea(g as Graphics2D?, r, c, true)
  }
}