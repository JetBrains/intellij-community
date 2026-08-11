// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.unusedLocal

import com.intellij.codeInspection.ex.InspectionElementsMergerBase
import org.jdom.Element

/**
 * Migrates user inspection-profile settings (enabled state, severity, and shared options such as `ignoreLambdaParameters`) from
 * the former combined `PyUnusedLocalInspection` to one of the inspections it was split into by PY-9687: the unused local-variable,
 * unused-parameter, and unused-function inspections. Without this, a user who had disabled or reconfigured `PyUnusedLocalInspection`
 * would silently get the new inspections back at their defaults.
 *
 * `PyUnusedLocalInspection` is deliberately no longer a live inspection short name — it is retired into a pure migration source.
 * If it stayed live it would consume its own serialized settings while it initialized, and because inspection initialization order
 * is not deterministic the mergers would often run after the settings were already gone, dropping the migration.
 *
 * Suppression is intentionally NOT inherited across the split: each merged inspection only answers to its own suppress id, so
 * [getSuppressIds] returns that id instead of letting the base fall back to the source tool name.
 */
abstract class PyUnusedSymbolInspectionMerger : InspectionElementsMergerBase() {
  /** Suppress id of the merged (new) inspection. */
  protected abstract val mergedSuppressId: String

  final override fun getSourceToolNames(): Array<String> = arrayOf(SOURCE_TOOL_NAME)

  final override fun getSuppressIds(): Array<String> = arrayOf(mergedSuppressId)

  final override fun merge(inspectionElements: Map<String, Element>): Element? {
    val merged = super.merge(inspectionElements)
    if (merged != null) return merged
    // super.merge() produces nothing when the source node carried no option children (e.g. a plain enable/disable toggle or a
    // severity change). Still carry over the enabled state and severity so the split stays transparent to such users.
    val source = inspectionElements[SOURCE_TOOL_NAME] ?: return null
    return Element(INSPECTION_TOOL_TAG).apply {
      setAttribute(CLASS_ATTR, mergedToolName)
      source.getAttributeValue(ENABLED_ATTR)?.let { setAttribute(ENABLED_ATTR, it) }
      source.getAttributeValue(ENABLED_BY_DEFAULT_ATTR)?.let { setAttribute(ENABLED_BY_DEFAULT_ATTR, it) }
      source.getAttributeValue(LEVEL_ATTR)?.let { setAttribute(LEVEL_ATTR, it) }
    }
  }

  private companion object {
    const val SOURCE_TOOL_NAME = "PyUnusedLocalInspection"

    // Inspection-profile serialization names (mirror InspectionProfileImpl/ToolsImpl, which are not visible from here).
    const val INSPECTION_TOOL_TAG = "inspection_tool"
    const val CLASS_ATTR = "class"
    const val ENABLED_ATTR = "enabled"
    const val ENABLED_BY_DEFAULT_ATTR = "enabled_by_default"
    const val LEVEL_ATTR = "level"
  }
}

class PyUnusedLocalVariableInspectionMerger : PyUnusedSymbolInspectionMerger() {
  override fun getMergedToolName(): String = "PyUnusedLocalVariableInspection"
  override val mergedSuppressId: String get() = "PyUnusedLocal"
}

class PyUnusedParameterInspectionMerger : PyUnusedSymbolInspectionMerger() {
  override fun getMergedToolName(): String = "PyUnusedParameterInspection"
  override val mergedSuppressId: String get() = "unused-parameter"
}

class PyUnusedFunctionInspectionMerger : PyUnusedSymbolInspectionMerger() {
  override fun getMergedToolName(): String = "PyUnusedFunctionInspection"
  override val mergedSuppressId: String get() = "unused-function"
}
