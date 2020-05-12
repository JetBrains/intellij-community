// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs

import com.intellij.util.ui.HtmlPanel
import com.intellij.util.ui.UIUtil.getLabelFont
import org.jetbrains.idea.svn.WorkingCopyFormat
import org.jetbrains.idea.svn.dialogs.CopiesPanel.formatWc
import java.awt.Font
import javax.swing.event.HyperlinkEvent

private class WorkingCopyInfoPanel : HtmlPanel() {
  var info: WCInfo? = null
  var upgradeFormats: Collection<WorkingCopyFormat> = emptyList()

  override fun getBodyFont(): Font = getLabelFont()

  override fun getBody(): String = info?.let { formatWc(it, upgradeFormats) }.orEmpty()

  override fun hyperlinkUpdate(e: HyperlinkEvent) = Unit
}