// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.LafIconLookup
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewUtils
import org.intellij.plugins.markdown.editor.livepreview.isLivePreviewEnabled
import org.intellij.plugins.markdown.editor.livepreview.toTextRange
import org.jetbrains.annotations.ApiStatus
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

internal class MarkdownLivePreviewCheckboxRenderer(private val project: Project, private val editor: EditorEx) : MarkdownLivePreviewElementRenderer {
  private val checkboxes = LinkedHashMap<Inlay<*>, Checkbox>()
  private var pressed: Checkbox? = null

  init {
    editor.addEditorMouseListener(object : EditorMouseListener {
      override fun mousePressed(event: EditorMouseEvent) {
        pressed = null
        val mouse = event.mouseEvent
        if (event.isConsumed || !SwingUtilities.isLeftMouseButton(mouse)) return
        val checkbox = event.checkbox() ?: return
        pressed = checkbox
        event.consume()
      }

      override fun mouseReleased(event: EditorMouseEvent) {
        val target = pressed ?: return
        pressed = null
        event.consume()
        val checkbox = event.checkbox() ?: return
        if (target === checkbox) toggle(checkbox)
      }

      override fun mouseExited(event: EditorMouseEvent) {
        editor.setCustomCursor(this@MarkdownLivePreviewCheckboxRenderer, null)
      }
    }, this)
    editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
      override fun mouseMoved(event: EditorMouseEvent) {
        val inlay = event.checkbox()?.inlay
        val cursor = if (inlay != null && inlay.renderer.enabled) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else null
        editor.setCustomCursor(this@MarkdownLivePreviewCheckboxRenderer, cursor)
      }
    }, this)
    editor.addPropertyChangeListener({ event ->
      if (event.propertyName == EditorEx.PROP_FONT_SIZE || event.propertyName == EditorEx.PROP_FONT_SIZE_2D) {
        checkboxes.keys.forEach { if (it.isValid) it.update() }
        refreshCursor()
      }
    }, this@MarkdownLivePreviewCheckboxRenderer)
  }

  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> {
    return listOf(MarkdownLivePreviewFold(
      (spec as MarkdownLivePreviewSpec.TaskCheckbox).concealRange.toTextRange(),
      decoration = CheckboxDecoration(),
    ))
  }

  private inner class CheckboxDecoration : MarkdownLivePreviewFoldDecoration {
    override fun create(region: FoldRegion): MarkdownLivePreviewMountedDecoration? = createDecoration(region)
  }

  private fun createDecoration(region: FoldRegion): MarkdownLivePreviewMountedDecoration? {
    val renderer = MarkdownLivePreviewCheckboxInlayRenderer(editor, region)
    val inlay = editor.inlayModel.addInlineElement(region.endOffset, false, renderer)?.takeIf { it.isValid } ?: return null
    return Checkbox(region, inlay).also { checkboxes[inlay] = it }
  }

  private fun EditorMouseEvent.checkbox(): Checkbox? {
    val target = inlay ?: return null
    return checkboxes[target]?.takeIf { target.isValid }
  }

  override fun documentChanged() = Unit

  internal fun refreshCursor() {
    val component = editor.contentComponent
    if (!component.isShowing) return
    val point = component.mousePosition ?: return
    val inlay = editor.inlayModel.getElementAt(point, MarkdownLivePreviewCheckboxInlayRenderer::class.java)
    val cursor = if (inlay != null && checkboxes.containsKey(inlay) && inlay.renderer.enabled) {
      Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }
    else {
      null
    }
    editor.setCustomCursor(this, cursor)
  }

  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) {
    refreshCursor()
  }

  private fun toggle(checkbox: Checkbox) {
    val inlay = checkbox.inlay
    if (!inlay.renderer.enabled || !editor.isLivePreviewEnabled()) return
    writeCommandAction(project).withName(MarkdownBundle.message("markdown.live.preview.toggle.task")).run<Throwable> {
      val region = checkbox.region
      if (!region.isValid || region.endOffset - region.startOffset < 3) return@run
      val document = editor.document
      val text = document.charsSequence
      val stateOffset = region.endOffset - 2
      if (!MarkdownLivePreviewUtils.isCheckbox(text, stateOffset - 1) ||
          text.subSequence(region.startOffset, stateOffset).toString() != checkbox.prefix) return@run
      document.replaceString(stateOffset, stateOffset + 1, if (text[stateOffset] == ' ') "x" else " ")
      inlay.repaint()
    }
  }

  override fun dispose() {
    checkboxes.values.toList().forEach(Disposer::dispose)
    if (!editor.isDisposed) editor.setCustomCursor(this, null)
  }

  private inner class Checkbox(
    val region: FoldRegion,
    val inlay: Inlay<MarkdownLivePreviewCheckboxInlayRenderer>,
  ) : MarkdownLivePreviewMountedDecoration {
    var prefix: String = sourcePrefix()
      private set

    private fun sourcePrefix(): String = editor.document.charsSequence.subSequence(region.startOffset, region.endOffset - 2).toString()

    override fun update(decoration: MarkdownLivePreviewFoldDecoration): Boolean {
      if (decoration !is CheckboxDecoration || !inlay.isValid || !region.isValid) return false
      prefix = sourcePrefix()
      inlay.repaint()
      return true
    }

    override fun dispose() {
      checkboxes.remove(inlay)
      if (pressed === this) pressed = null
      Disposer.dispose(inlay)
    }
  }
}

/** Paints a checkbox from its current document marker. */
@ApiStatus.Internal
class MarkdownLivePreviewCheckboxInlayRenderer internal constructor(
  private val editor: EditorEx,
  private val region: FoldRegion,
) : EditorCustomElementRenderer {
  val checked: Boolean
    get() = region.isValid && editor.document.charsSequence.getOrNull(region.endOffset - 2)?.lowercaseChar() == 'x'

  internal val enabled: Boolean get() = !editor.isViewer && editor.document.isWritable

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    return (editor.lineHeight * CHECKBOX_LINE_HEIGHT_RATIO).roundToInt().coerceAtLeast(1)
  }

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    val icon = LafIconLookup.getIcon("checkBox", selected = checked, enabled = enabled)
    val scale = minOf(targetRegion.width.toDouble() / icon.iconWidth, targetRegion.height.toDouble() / icon.iconHeight)
    val child = g.create() as? Graphics2D ?: return
    try {
      child.translate(
        targetRegion.x + (targetRegion.width - icon.iconWidth * scale) / 2,
        targetRegion.y + (targetRegion.height - icon.iconHeight * scale) / 2,
      )
      child.scale(scale, scale)
      icon.paintIcon(editor.contentComponent, child, 0, 0)
    }
    finally {
      child.dispose()
    }
  }

  private companion object {
    private const val CHECKBOX_LINE_HEIGHT_RATIO = 0.75f
  }
}
