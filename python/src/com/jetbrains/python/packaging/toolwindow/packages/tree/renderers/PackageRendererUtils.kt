// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.packages.tree.renderers

import com.intellij.util.ui.UIUtil
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import java.awt.Color
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode

internal object PackageRendererUtils {
  fun createBasicPanel(): JPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    isOpaque = false
  }

  fun getBackgroundForState(isSelected: Boolean): Color = when {
    isSelected -> UIUtil.getTreeSelectionBackground(true)
    else -> UIUtil.getTreeBackground()
  }

  fun extractPackage(value: Any?): DisplayablePackage? = when (value) {
    is DisplayablePackage -> value
    is DefaultMutableTreeNode -> value.userObject as? DisplayablePackage
    else -> null
  }
}