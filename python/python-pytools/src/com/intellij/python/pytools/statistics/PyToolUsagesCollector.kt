// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolsState
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.python.pytools.lsp.PyLspToolSettings
import com.intellij.util.ThreeState

class PyToolUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  object Helper {
    fun logConfigurationChanged(
        project: Project,
        tool: PyTool,
        enabled: Boolean,
        executableDiscoveryMode: ExecutableDiscoveryMode,
        inspections: Boolean? = null,
        completions: Boolean? = null,
        inlayHints: Boolean? = null,
        documentation: Boolean? = null,
        formatting: Boolean? = null,
        sortImports: Boolean? = null,
    ) {
      CONFIGURATION_CHANGED.log(
        project,
        toolNameField with tool.fusId,
        enabledField with enabled,
        inspectionsField with inspections.toThreeState,
        completionsField with completions.toThreeState,
        inlayHintsField with inlayHints.toThreeState,
        documentationField with documentation.toThreeState,
        formattingField with formatting.toThreeState,
        sortImportsField with sortImports.toThreeState,
        executableDiscoveryModeField with executableDiscoveryMode,
      )
    }

    fun logDisableRule(project: Project, forFile: Boolean) {
      DISABLE_RULE.log(project, forFile)
    }

    fun logToolInstalled(project: Project, tool: PyTool) {
      TOOL_INSTALLED.log(project, tool.fusId)
    }
    fun logToolUpdated(project: Project, tool: PyTool) {
      TOOL_UPDATED.log(project, tool.fusId)
    }

    /**
     * Convenience wrapper that derives enabled/discoveryMode/customPath from [PyToolsState] and
     * feature-flag values from a per-tool [PyLspToolSettings] before calling
     * [logConfigurationChanged]. Pass [formatting] / [sortImports] for tools that expose those
     * (currently only Ruff).
     */
    fun logPyToolConfigurationChanged(
      project: Project,
      tool: PyTool,
      settings: PyLspToolSettings,
      formatting: Boolean? = null,
      sortImports: Boolean? = null,
    ) {
      val entry = PyToolsState.getInstance(project).getEntry(tool)
      logConfigurationChanged(
        project = project,
        tool = tool,
        enabled = entry.enabled,
        inspections = settings.inspections,
        completions = settings.completions,
        inlayHints = settings.inlayHints,
        documentation = settings.documentation,
        formatting = formatting,
        sortImports = sortImports,
        executableDiscoveryMode = entry.discoveryMode,
      )
    }
  }

}

private val GROUP = EventLogGroup("python.lsp", 5)

private val toolNameField = EventFields.StringValidatedByDictionary("tool_name", "python_packages.ndjson")
private val enabledField = EventFields.Boolean("enabled")
private val inspectionsField = EventFields.Enum("inspections", ThreeState::class.java)
private val completionsField = EventFields.Enum("completions", ThreeState::class.java)
private val inlayHintsField = EventFields.Enum("inlay_hints", ThreeState::class.java)
private val documentationField = EventFields.Enum("documentation", ThreeState::class.java)
private val formattingField = EventFields.Enum("formatting", ThreeState::class.java)
private val sortImportsField = EventFields.Enum("sort_imports", ThreeState::class.java)
private val executableDiscoveryModeField = EventFields.Enum("executable_discovery_mode", ExecutableDiscoveryMode::class.java)

private val CONFIGURATION_CHANGED = GROUP.registerVarargEvent(
  "configuration.changed",
  toolNameField,
  enabledField,
  inspectionsField,
  completionsField,
  inlayHintsField,
  documentationField,
  formattingField,
  sortImportsField,
  executableDiscoveryModeField,
)

private val DISABLE_RULE = GROUP.registerEvent(
  "disable_rule",
  EventFields.Boolean("for_file")
)

private val TOOL_INSTALLED = GROUP.registerEvent(
  "installed",
  toolNameField,
)
private val TOOL_UPDATED = GROUP.registerEvent(
  "updated",
  toolNameField,
)

private val Boolean?.toThreeState: ThreeState
  get() = when (this) {
    true -> ThreeState.YES
    false -> ThreeState.NO
    null -> ThreeState.UNSURE
  }
