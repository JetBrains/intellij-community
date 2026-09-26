// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeEx

// Looks similar to [com.intellij.xdebugger.impl.collection.visualizer.XDebuggerNodeLinkActionProvider]
// But this one works on the backend. Currentlz used for adding additional "View as Image" action hyperlinks to variables in debug toolwindow.
interface PyDebugValueAdditionalHyperlinkProvider {
  fun provideHyperlink(debugValue: PyDebugValue, valueNode: XValueNodeEx): XDebuggerTreeNodeHyperlink?

  companion object {
    private val EP_NAME =
      ExtensionPointName<PyDebugValueAdditionalHyperlinkProvider>("com.jetbrains.python.debugger.additionalHyperlinkProvider")

    fun computeHyperlink(debugValue: PyDebugValue, valueNode: XValueNodeEx) {
      for (provider in EP_NAME.extensionList) {
        val hyperlink = provider.provideHyperlink(debugValue, valueNode)

        if (hyperlink != null) {
          valueNode.addAdditionalHyperlink(hyperlink)
        }
      }
    }
  }
}