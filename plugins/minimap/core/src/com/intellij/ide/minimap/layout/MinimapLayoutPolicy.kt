// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layout

import com.intellij.ide.minimap.model.MinimapSourceSoftWrap
import com.intellij.ide.minimap.settings.MinimapScaleMode
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.util.LinkedHashMap

/**
 * Controls minimap layout for an editor: panel dimensions, per-line span overrides, and scale mode.
 *
 * Register via `com.intellij.minimapLayoutPolicy`.
 *
 * For layout profile and scale mode, the first applicable policy wins.
 * For line spans and source soft wraps, applicable providers are merged in registration order; the last provider wins for the same line.
 * For fold region filtering, any policy returning `false` suppresses the region.
 */
@ApiStatus.OverrideOnly
interface MinimapLayoutPolicy {
  fun isApplicable(editor: Editor): Boolean = true

  /**
   * Returns layout profile override for [editor], or `null` to defer to the next policy.
   */
  fun getLayoutProfile(editor: Editor): MinimapLayoutProfile? = null

  /**
   * Version used to invalidate cached line projection when external visual geometry changes
   * (for example, notebook cell output inlay heights).
   */
  fun getProjectionVersion(editor: Editor, document: Document, logicalLineCount: Int): Long = 0

  /**
   * Returns logical-line -> span overrides.
   * Invalid line numbers are ignored. Values are normalized to at least `1`.
   *
   * A span of:
   * - `1` keeps the default one-slot mapping;
   * - `N > 1` expands a logical line into multiple projected slots;
   * - `0` is reserved for internal compression.
   */
  fun getLineSpanOverrides(editor: Editor, document: Document, logicalLineCount: Int): Map<Int, Int> = emptyMap()

  /**
   * Returns logical-line -> source soft wraps to use instead of the editor's current soft-wrap model.
   * This is useful for visual-only presentations that hide source text from the editor soft-wrap cache.
   */
  fun getSourceSoftWraps(editor: Editor, document: Document, logicalLineCount: Int): Map<Int, List<MinimapSourceSoftWrap>> = emptyMap()

  fun getLineProjectionData(editor: Editor, document: Document, logicalLineCount: Int): MinimapLineProjectionData {
    val lineSpanOverrides = getLineSpanOverrides(editor, document, logicalLineCount)
    val sourceSoftWrapsByLine = getSourceSoftWraps(editor, document, logicalLineCount)
    if (lineSpanOverrides.isEmpty() && sourceSoftWrapsByLine.isEmpty()) return MinimapLineProjectionData.EMPTY
    return MinimapLineProjectionData(lineSpanOverrides, sourceSoftWrapsByLine)
  }

  /**
   * Controls whether a collapsed [region] should be applied to minimap line projection.
   * Return `false` for visual-only folds that should not hide underlying text in minimap.
   */
  fun shouldUseCollapsedFoldRegion(
    editor: Editor,
    document: Document,
    logicalLineCount: Int,
    region: FoldRegion,
    startLine: Int,
    endLine: Int,
  ): Boolean = true

  fun getScaleMode(editor: Editor, settingsScaleMode: MinimapScaleMode): MinimapScaleMode = settingsScaleMode

  fun supportsFitMode(editor: Editor): Boolean = true

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<MinimapLayoutPolicy> =
      ExtensionPointName("com.intellij.minimapLayoutPolicy")

    fun forLayoutProfile(editor: Editor): MinimapLayoutProfile {
      for (policy in EP_NAME.extensionList) {
        if (!policy.isApplicable(editor)) continue
        val profile = policy.getLayoutProfile(editor) ?: continue
        return profile.normalized()
      }
      return MinimapLayoutProfile.DEFAULT
    }

    fun collectLineProjectionData(editor: Editor, document: Document, logicalLineCount: Int): MinimapLineProjectionData {
      if (logicalLineCount <= 0) return MinimapLineProjectionData.EMPTY

      var mergedLineSpans: MutableMap<Int, Int>? = null
      var mergedSourceSoftWraps: MutableMap<Int, List<MinimapSourceSoftWrap>>? = null
      for (policy in EP_NAME.extensionList) {
        if (!policy.isApplicable(editor)) continue
        val data = policy.getLineProjectionData(editor, document, logicalLineCount)
        mergedLineSpans = mergeLineSpanOverrides(mergedLineSpans, data.lineSpanOverrides, logicalLineCount)
        mergedSourceSoftWraps = mergeSourceSoftWraps(mergedSourceSoftWraps, data.sourceSoftWrapsByLine, document, logicalLineCount)
      }

      val lineSpans = mergedLineSpans?.takeIf { it.isNotEmpty() } ?: emptyMap()
      val sourceSoftWraps = mergedSourceSoftWraps?.takeIf { it.isNotEmpty() } ?: emptyMap()
      if (lineSpans.isEmpty() && sourceSoftWraps.isEmpty()) return MinimapLineProjectionData.EMPTY
      return MinimapLineProjectionData(lineSpans, sourceSoftWraps)
    }

    fun collect(editor: Editor, document: Document, logicalLineCount: Int): Map<Int, Int> {
      if (logicalLineCount <= 0) return emptyMap()

      var merged: MutableMap<Int, Int>? = null
      for (policy in EP_NAME.extensionList) {
        if (!policy.isApplicable(editor)) continue
        merged = mergeLineSpanOverrides(merged, policy.getLineSpanOverrides(editor, document, logicalLineCount), logicalLineCount)
      }
      return merged ?: emptyMap()
    }

    fun projectionVersion(editor: Editor, document: Document, logicalLineCount: Int): Long {
      if (logicalLineCount <= 0) return 0

      var version = 0L
      for (policy in EP_NAME.extensionList) {
        if (!policy.isApplicable(editor)) continue
        version = version * 31L + policy.getProjectionVersion(editor, document, logicalLineCount)
      }
      return version
    }

    private fun mergeLineSpanOverrides(targetRaw: MutableMap<Int, Int>?,
                                       overrides: Map<Int, Int>,
                                       logicalLineCount: Int): MutableMap<Int, Int>? {
      if (overrides.isEmpty()) return targetRaw
      val target = targetRaw ?: LinkedHashMap()
      for ((logicalLine, spanRaw) in overrides) {
        if (logicalLine !in 0 until logicalLineCount) continue
        target[logicalLine] = spanRaw.coerceAtLeast(1)
      }
      return target
    }

    private fun mergeSourceSoftWraps(targetRaw: MutableMap<Int, List<MinimapSourceSoftWrap>>?,
                                     overrides: Map<Int, List<MinimapSourceSoftWrap>>,
                                     document: Document,
                                     logicalLineCount: Int): MutableMap<Int, List<MinimapSourceSoftWrap>>? {
      if (overrides.isEmpty()) return targetRaw
      val target = targetRaw ?: LinkedHashMap()
      for ((logicalLine, wrapsRaw) in overrides) {
        if (logicalLine !in 0 until logicalLineCount) continue
        val lineStartOffset = document.getLineStartOffset(logicalLine)
        val lineEndOffset = document.getLineEndOffset(logicalLine)
        if (lineEndOffset - lineStartOffset <= 1) continue

        val wraps = wrapsRaw
          .asSequence()
          .filter { it.startOffset in (lineStartOffset + 1) until lineEndOffset }
          .sortedBy { it.startOffset }
          .distinctBy { it.startOffset }
          .map { MinimapSourceSoftWrap(it.startOffset, it.indentColumns.coerceAtLeast(0)) }
          .toList()
        if (wraps.isNotEmpty()) {
          target[logicalLine] = wraps
        }
      }
      return target
    }

    fun shouldUseCollapsedFoldRegion(
      editor: Editor,
      document: Document,
      logicalLineCount: Int,
      region: FoldRegion,
      startLine: Int,
      endLine: Int,
    ): Boolean {
      for (policy in EP_NAME.extensionList) {
        if (!policy.isApplicable(editor)) continue
        if (!policy.shouldUseCollapsedFoldRegion(editor, document, logicalLineCount, region, startLine, endLine)) {
          return false
        }
      }
      return true
    }

    fun getEffectiveScaleMode(editor: Editor, settingsScaleMode: MinimapScaleMode): MinimapScaleMode {
      for (policy in EP_NAME.extensionList) {
        if (!policy.isApplicable(editor)) continue
        return policy.getScaleMode(editor, settingsScaleMode)
      }
      return settingsScaleMode
    }

    fun supportsFitMode(editor: Editor): Boolean {
      for (policy in EP_NAME.extensionList) {
        if (!policy.isApplicable(editor)) continue
        if (!policy.supportsFitMode(editor)) return false
      }
      return true
    }
  }
}
