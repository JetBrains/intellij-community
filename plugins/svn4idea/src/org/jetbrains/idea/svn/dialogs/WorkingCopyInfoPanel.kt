// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.dialogs

import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.HtmlChunk.*
import com.intellij.ui.ColorUtil.toHex
import com.intellij.ui.JBColor
import com.intellij.util.ui.HtmlPanel
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.idea.svn.NestedCopyType
import org.jetbrains.idea.svn.SvnBundle.message
import org.jetbrains.idea.svn.WorkingCopyFormat
import org.jetbrains.idea.svn.api.Depth
import org.jetbrains.idea.svn.dialogs.CopiesPanel.*
import java.awt.Font
import javax.swing.event.HyperlinkEvent

private class WorkingCopyInfoPanel : HtmlPanel() {
  var info: WCInfo? = null
  var upgradeFormats: Collection<WorkingCopyFormat> = emptyList()

  override fun getBodyFont(): Font = StartupUiUtil.getLabelFont()
  override fun getBody(): String = getBodyHtml().toString()
  override fun hyperlinkUpdate(e: HyperlinkEvent) = Unit

  private fun getBodyHtml(): HtmlChunk = info?.let { UiBuilder(it, upgradeFormats).build() } ?: empty()
}

private class UiBuilder(private val info: WCInfo, private val upgradeFormats: Collection<WorkingCopyFormat>) {
  fun build(): HtmlChunk =
    HtmlBuilder().apply {
      append(pathRow())
      append(urlRow())
      append(formatRow())
      append(depthRow())
      append(nestedTypeRow())
      append(isRootRow())

      if (!info.hasError()) {
        append(cleanupRow())
        append(configureBranchesRow())
        append(mergeFromRow())
      }
    }.wrapWith("table")

  private fun pathRow(): HtmlChunk = tr().child(td(3).child(text(info.path).bold()))

  private fun urlRow(): HtmlChunk {
    val tr = tr().child(td().addText(message("label.working.copy.url")))

    return if (info.hasError())
      tr.child(td(2).attr("color", toHex(JBColor.red)).addText(info.errorMessage))
    else
      tr.child(td(2).addText(info.url.toDecodedString()))
  }

  private fun formatRow(): HtmlChunk {
    val tr = tr().child(td().addText(message("label.working.copy.format")))

    return if (upgradeFormats.size <= 1)
      tr.child(td(2).addText(info.format.displayName))
    else
      tr.children(
        td().addText(info.format.displayName),
        td().child(link(CHANGE_FORMAT, message("link.change.format")))
      )
  }

  private fun depthRow(): HtmlChunk {
    val tr = tr().child(td().addText(message("label.working.copy.depth")))

    return if (Depth.INFINITY == info.stickyDepth || info.hasError())
      tr.child(td(2).addText(info.stickyDepth.displayName))
    else
      tr.children(
        td().addText(info.stickyDepth.displayName),
        td().child(link(FIX_DEPTH, message("link.fix.depth")))
      )
  }

  private fun nestedTypeRow(): HtmlChunk {
    val type = info.type
    if (NestedCopyType.external != type && NestedCopyType.switched != type) return empty()

    return tr().child(td(3).child(text(type.displayName).italic()))
  }

  private fun isRootRow(): HtmlChunk {
    if (!info.isIsWcRoot) return empty()

    return tr().child(td(3).child(text(message("label.working.copy.root")).italic()))
  }

  private fun cleanupRow(): HtmlChunk {
    if (!info.format.isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN)) return empty()

    return tr().child(td(3).child(link(CLEANUP, message("cleanup.action.name"))))
  }

  private fun configureBranchesRow(): HtmlChunk =
    tr().child(td(3).child(link(CONFIGURE_BRANCHES, message("action.name.configure.branches"))))

  private fun mergeFromRow(): HtmlChunk =
    tr().child(td(3).child(link(MERGE_FROM, message("action.name.merge.from.ellipsis")).bold()))
}

private fun tr(): Element = tag("tr").attr("valign", "top")
private fun td(): Element = tag("td")
private fun td(colspan: Int = -1): Element = tag("td").attr("colspan", colspan)