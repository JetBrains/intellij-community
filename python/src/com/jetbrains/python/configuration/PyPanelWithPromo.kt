// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.not
import com.intellij.ui.dsl.builder.*
import com.jetbrains.python.createPromoPanel
import javax.swing.JPanel

/**
 * Shows either promo-panel (if [promoMode]) or [whenPromoDisabled]
 */
internal class PyPanelWithPromo(private val whenPromoDisabled: JPanel) {
  private val root = PropertyGraph()
  private val _promoMode = root.property(false)
  var promoMode: Boolean by _promoMode
  val panel: JPanel = panel {
    row {
      cell(createPromoPanel()).resizableColumn().align(AlignY.CENTER + AlignX.FILL).applyToComponent { putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap(top = false, bottom = false)) }
    }.resizableRow().visibleIf(_promoMode)
    row {
      cell(whenPromoDisabled).resizableColumn().align(AlignY.FILL + AlignX.FILL).applyToComponent { putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap(top = false, bottom = false)) }
    }.resizableRow().visibleIf(_promoMode.not())
  }
}