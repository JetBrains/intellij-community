package com.jetbrains.performancePlugin.remotedriver.fixtures

import org.assertj.swing.core.BasicComponentFinder
import org.assertj.swing.driver.CellRendererReader
import java.awt.Component
import java.awt.Container

class AccessibleNameCellRendererReader : CellRendererReader {
  override fun valueFrom(c: Component?): String? {
    var accessibleName = c?.accessibleContext?.accessibleName
    if (accessibleName == null && c is Container) {
      accessibleName = findSomeChildComponentAccessibleName(c)
    }
    return accessibleName ?: ""
  }

  private fun findSomeChildComponentAccessibleName(c: Container): String? {
    return BasicComponentFinder.finderWithCurrentAwtHierarchy().findAll(c) { c -> c.accessibleContext.accessibleName != null }
      .firstOrNull()?.accessibleContext?.accessibleName
  }
}