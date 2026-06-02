// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.util.ThreeState

class PyToolUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  object Helper {
    /**
     * Single emit point for `configuration.changed`. The tool itself supplies the field values
     * via [PyTool.configurationFusSnapshot], so every call site only needs the tool reference
     * plus the originating UI [source].
     */
    fun logConfigurationChanged(project: Project, tool: PyTool, source: PyToolActionSource) {
      val s = tool.configurationFusSnapshot(project)
      CONFIGURATION_CHANGED.log(
        project,
        toolNameField with tool.fusId,
        sourceField with source,
        enabledField with s.enabled,
        inspectionsField with s.inspections.toThreeState,
        completionsField with s.completions.toThreeState,
        inlayHintsField with s.inlayHints.toThreeState,
        documentationField with s.documentation.toThreeState,
        formattingField with s.formatting.toThreeState,
        sortImportsField with s.sortImports.toThreeState,
        executableDiscoveryModeField with s.executableDiscoveryMode,
        customPathField with s.customPath,
      )
    }

    fun logDisableRule(project: Project, forFile: Boolean) {
      DISABLE_RULE.log(project, forFile)
    }

    fun logToolInstalled(project: Project, tool: PyTool, source: PyToolActionSource) {
      TOOL_INSTALLED.log(project, tool.fusId, source)
    }
    fun logToolUpdated(project: Project, tool: PyTool, source: PyToolActionSource) {
      TOOL_UPDATED.log(project, tool.fusId, source)
    }
  }

}

private val GROUP = EventLogGroup("python.lsp", 7)

/**
 * UI place from which a Python-tool action (install / upgrade / configuration change) was
 * triggered. Extend when new entry points (notifications, intentions, editor banners, ...)
 * start logging these events.
 */
enum class PyToolActionSource {
  /** The Python External Tools settings page — the table itself (path-column icons and the page-level apply). */
  SETTINGS_TABLE,
  /** A per-tool detail configurable nested inside the Python External Tools settings page. */
  SETTINGS_DETAIL,
}

private val toolNameField = EventFields.StringValidatedByDictionary("tool_name", "python_packages.ndjson")
private val sourceField = EventFields.Enum("source", PyToolActionSource::class.java)
private val enabledField = EventFields.Boolean("enabled")
private val inspectionsField = EventFields.Enum("inspections", ThreeState::class.java)
private val completionsField = EventFields.Enum("completions", ThreeState::class.java)
private val inlayHintsField = EventFields.Enum("inlay_hints", ThreeState::class.java)
private val documentationField = EventFields.Enum("documentation", ThreeState::class.java)
private val formattingField = EventFields.Enum("formatting", ThreeState::class.java)
private val sortImportsField = EventFields.Enum("sort_imports", ThreeState::class.java)
private val executableDiscoveryModeField = EventFields.Enum("executable_discovery_mode", ExecutableDiscoveryMode::class.java)
/**
 * True when the user has overridden the tool's executable with a "Browse for executable"
 * custom path; false when the executable is auto-detected (via SDK or `$PATH`).
 */
private val customPathField = EventFields.Boolean("custom_path")

private val CONFIGURATION_CHANGED = GROUP.registerVarargEvent(
  "configuration.changed",
  toolNameField,
  sourceField,
  enabledField,
  inspectionsField,
  completionsField,
  inlayHintsField,
  documentationField,
  formattingField,
  sortImportsField,
  executableDiscoveryModeField,
  customPathField,
)

private val DISABLE_RULE = GROUP.registerEvent(
  "disable_rule",
  EventFields.Boolean("for_file")
)

private val TOOL_INSTALLED = GROUP.registerEvent(
  "installed",
  toolNameField,
  sourceField,
)
private val TOOL_UPDATED = GROUP.registerEvent(
  "updated",
  toolNameField,
  sourceField,
)

private val Boolean?.toThreeState: ThreeState
  get() = when (this) {
    true -> ThreeState.YES
    false -> ThreeState.NO
    null -> ThreeState.UNSURE
  }
