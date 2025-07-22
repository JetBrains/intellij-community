// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.ui.dsl.builder.*
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBFont
import com.jetbrains.python.icons.PythonIcons
import javax.swing.JPanel

/**
 * Panel that suggests user to buy Pro version
 */
@RequiresEdt
internal fun createPromoPanel(): JPanel = panel {
  row {
    icon(PythonIcons.Python.Pycharm32).resizableColumn().align(AlignX.RIGHT + AlignY.TOP)
    panel {
      row {
        @Suppress("DialogTitleCapitalization") // PyCharm Pro is literally how we want to see it
        text(PyBundle.message("pycharm.free.mode.upgrade.title")).align(AlignY.BOTTOM).applyToComponent {
          font = JBFont.h1()
        }
      }
      row {
        text(PyBundle.message("pycharm.free.mode.upgrade.body")).align(AlignY.TOP).applyToComponent { putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap(top = false, bottom = false)) }
      }
      row {
        button(PyBundle.message("pycharm.free.mode.upgrade.button")) {
          BrowserLauncher.instance.open("https://www.jetbrains.com/pycharm/buy/?section=commercial&billing=yearly")
        }.applyToComponent {
          putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
        }
      }
    }.resizableColumn().align(AlignX.LEFT)
  }
}