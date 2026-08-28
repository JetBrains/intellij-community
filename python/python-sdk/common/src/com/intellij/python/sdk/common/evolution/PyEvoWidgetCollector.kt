// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.common.evolution

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Usage statistics for the Evo Python interpreter widget — the status-bar widget and its popup.
 *
 * Declared here (common) for the same reason the widget's registry keys are: this group is logged from **both**
 * processes. The frontend knows what the user clicked (which tool node was opened, which row was picked, which
 * affordance was used); only the backend knows whether applying it actually worked. A group split across the two
 * modules would split the funnel with it, so the one cohesive group lives where both sides can reach it.
 *
 * The two sides are independent FUS recorders, so in split mode the platform stamps its own `product_mode`
 * (`frontend` / `backend`, or `monolith` when there is no split) onto every event — see
 * `StatisticsEventLogger.createEventLogGroup`. A funnel query therefore has to join [interpreterSelected] and
 * [interpreterApplied] **on `product_mode`**, not assume one stream.
 */
@ApiStatus.Internal
object PyEvoWidgetCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("python.sdk.widget", 2)

  override fun getGroup(): EventLogGroup = GROUP

  /** Where in the popup a row came from — the section, as opposed to the node identity it configures. */
  enum class Source {
    /** A tool node's own environment list. */
    TOOL_NODE,

    /** The "Associated environments" node (rows are existing SDKs). */
    ASSOCIATED,

    /** A "Shortcuts" autoconfigure suggestion, shown when no interpreter is set. */
    SHORTCUTS,

    /** A base interpreter picked from the expanded per-version listing. */
    EXPANDED_VERSION,

    /** A base Python picked in a row's rebuild panel, which destroys that environment and builds it again. */
    RECREATE,

    /** A Python version row in an "Add new" submenu. */
    ADD_NEW_VERSION,

    /** The "download and install" row for a version the machine does not have. */
    INSTALL_ROW,
  }

  /** Which kind of [PyInterpreterRef] a pick resolved to. Mirrors that sealed interface, one constant per subtype. */
  enum class RefKind { EXISTING_SDK, DETECTED_PATH, CREATE_ENV, AUTOCONFIGURE }

  /** Whether a backend call succeeded. */
  enum class Outcome { OK, ERROR }

  /**
   * How expanding a tool node ended. Distinguishes the two non-failure disappointments from a real failure, because
   * they mean different things to a user and to us: [EMPTY] is a tool that answered with nothing to offer, while
   * [ERROR] is a tool (or an RPC) that broke.
   */
  enum class NodeOutcome {
    /** The node loaded and has rows. */
    OK,

    /** The tool answered, but with nothing to show (a disabled node with a warning sign). */
    EMPTY,

    /** The backend reported a failure, or could not be asked at all. */
    ERROR,
  }

  /**
   * A popup affordance that is not a row action — none of these goes through the action system, so without this event
   * they leave no trace at all.
   */
  enum class Control {
    /** A tool node's inline reload icon (force re-scan, bypassing the backend cache). */
    RELOAD,

    /** A row's inline icon, opening the panel of base Pythons its environment could be built on. */
    BASE_PYTHON_PANEL,

    /** The gear on the "Select Environment" header, opening Settings | Python | Tools | Package Manager. */
    GEAR_SETTINGS,

    /** A failed row was clicked, opening the Python Process Output tool window on that run. */
    PROCESS_OUTPUT,

    /** The "Manage Packages" row, opening the Python Packages tool window. */
    MANAGE_PACKAGES,
  }

  private val NODE_KIND = EventFields.Enum("node_kind", EvoNodeKind::class.java)

  /**
   * The backing tool's `PyExecutable.fusId`, present only for an [EvoNodeKind.TOOL] node.
   *
   * The same field the rest of the Python tooling reports a tool by (`PyToolUsagesCollector`), validated against the
   * same well-known-package dictionary — so a tool means the same thing in this group as in that one, and adding a tool
   * needs no change here at all.
   */
  private val TOOL_NAME = EventFields.StringValidatedByDictionary("tool_name", "python_packages.ndjson")
  private val SOURCE = EventFields.Enum("source", Source::class.java)
  private val REF_KIND = EventFields.Enum("ref_kind", RefKind::class.java)
  private val OUTCOME = EventFields.Enum("outcome", Outcome::class.java)
  private val NODE_OUTCOME = EventFields.Enum("node_outcome", NodeOutcome::class.java)
  private val CONTROL = EventFields.Enum("control", Control::class.java)

  private val HAS_INTERPRETER = EventFields.Boolean("has_interpreter")

  /** Bucketed rather than exact: the number of installed tools is small and stable, and buckets keep it non-identifying. */
  private val TOOL_COUNT = EventFields.BoundedInt("tool_count", intArrayOf(0, 1, 2, 3, 4, 5, 6, 8, 10))

  /** True when the node was re-scanned via its reload icon rather than opened for the first time. */
  private val IS_RELOAD = EventFields.Boolean("is_reload")

  /** True when the backend measured this tool as slow (the reason it offers a reload icon at all). */
  private val WAS_SLOW = EventFields.Boolean("was_slow")

  /** True when a base interpreter had to be downloaded and installed before the environment could be created. */
  private val DOWNLOADED_BASE = EventFields.Boolean("downloaded_base")

  private val POPUP_OPENED = GROUP.registerEvent("popup.opened", HAS_INTERPRETER, TOOL_COUNT)

  private val NODE_EXPANDED = GROUP.registerVarargEvent(
    "node.expanded", NODE_KIND, TOOL_NAME, NODE_OUTCOME, IS_RELOAD, WAS_SLOW, EventFields.DurationMs,
  )

  private val INTERPRETER_SELECTED = GROUP.registerVarargEvent("interpreter.selected", NODE_KIND, TOOL_NAME, REF_KIND, SOURCE)

  private val INTERPRETER_APPLIED = GROUP.registerVarargEvent(
    "interpreter.applied", NODE_KIND, TOOL_NAME, REF_KIND, OUTCOME, DOWNLOADED_BASE, EventFields.DurationMs,
  )

  private val CONTROL_USED = GROUP.registerVarargEvent("control.used", CONTROL, NODE_KIND, TOOL_NAME)

  private val BACKEND_ACTION_PERFORMED = GROUP.registerVarargEvent("backend.action.performed", NODE_KIND, TOOL_NAME, OUTCOME)

  /**
   * Adds the two fields that say which node an event is about.
   *
   * A tool name is added only for a [EvoNodeKind.TOOL] node; for every other kind the enum already exhausts the
   * node's identity, and an absent `tool_name` is what marks that.
   */
  private fun MutableList<EventPair<*>>.addNode(node: EvoNodeStats) {
    add(NODE_KIND.with(node.kind))
    node.fusId?.takeIf { node.kind == EvoNodeKind.TOOL }?.let { add(TOOL_NAME.with(it)) }
  }

  /** The widget popup was opened, against a target with (or without) a configured interpreter. */
  fun popupOpened(project: Project?, hasInterpreter: Boolean, toolCount: Int): Unit =
    POPUP_OPENED.log(project, hasInterpreter, toolCount)

  /** A tool node finished loading its environment list — however that ended. */
  fun nodeExpanded(
    project: Project?,
    node: EvoNodeStats,
    outcome: NodeOutcome,
    isReload: Boolean,
    wasSlow: Boolean,
    durationMs: Long,
  ): Unit = NODE_EXPANDED.log(project) {
    addNode(node)
    add(NODE_OUTCOME.with(outcome))
    add(IS_RELOAD.with(isReload))
    add(WAS_SLOW.with(wasSlow))
    add(EventFields.DurationMs.with(durationMs))
  }

  /**
   * A node whose submenu is built from data already in hand was opened — today the "Associated environments" node.
   *
   * Reported as the same `node.expanded` event so "which sections do people open" stays a single query, but without
   * the scan fields: there was no scan, and a fabricated `duration_ms` of 0 would drag the tool-scan timings it exists
   * to measure toward zero. Their absence is what marks a section as static.
   */
  fun staticNodeOpened(project: Project?, node: EvoNodeStats): Unit = NODE_EXPANDED.log(project) {
    addNode(node)
    add(NODE_OUTCOME.with(NodeOutcome.OK))
  }

  /** The user picked a row that switches or creates an environment — intent, before the backend has answered. */
  fun interpreterSelected(project: Project?, node: EvoNodeStats, refKind: RefKind, source: Source): Unit =
    INTERPRETER_SELECTED.log(project) {
      addNode(node)
      add(REF_KIND.with(refKind))
      add(SOURCE.with(source))
    }

  /** The backend finished applying a pick. Paired with [interpreterSelected]; see the note on `product_mode` above. */
  fun interpreterApplied(
    project: Project?,
    node: EvoNodeStats,
    refKind: RefKind,
    outcome: Outcome,
    downloadedBase: Boolean,
    durationMs: Long,
  ): Unit = INTERPRETER_APPLIED.log(project) {
    addNode(node)
    add(REF_KIND.with(refKind))
    add(OUTCOME.with(outcome))
    add(DOWNLOADED_BASE.with(downloadedBase))
    add(EventFields.DurationMs.with(durationMs))
  }

  /** One of the popup's non-action affordances was used. */
  fun controlUsed(
    project: Project?,
    control: Control,
    node: EvoNodeStats = EvoNodeStats(EvoNodeKind.OTHER),
  ): Unit = CONTROL_USED.log(project) {
    add(CONTROL.with(control))
    addNode(node)
  }

  /** A backend-dispatched node action (the "Advanced" add-interpreter rows) ran. */
  fun backendActionPerformed(project: Project?, node: EvoNodeStats, outcome: Outcome): Unit =
    BACKEND_ACTION_PERFORMED.log(project) {
      addNode(node)
      add(OUTCOME.with(outcome))
    }
}

/**
 * Which node an event is about: its [kind], plus the backing tool's `PyExecutable.fusId` when it has one.
 *
 * Carried as one value rather than two parameters because every event reports both together, and because the pairing
 * is a rule — a tool name without [EvoNodeKind.TOOL] is meaningless, and is dropped rather than reported.
 */
@ApiStatus.Internal
data class EvoNodeStats(val kind: EvoNodeKind, val fusId: @NonNls String? = null) {
  companion object {
    /** The identity of a backend-contributed node, taken straight from its DTO. */
    fun of(node: EvoNodeDto): EvoNodeStats = EvoNodeStats(node.kind, node.fusId)
  }
}

/** Which [PyEvoWidgetCollector.RefKind] this selector is. Shared, because both sides report a ref they are holding. */
@ApiStatus.Internal
fun PyInterpreterRef.evoRefKind(): PyEvoWidgetCollector.RefKind = when (this) {
  is PyInterpreterRef.ExistingSdk -> PyEvoWidgetCollector.RefKind.EXISTING_SDK
  is PyInterpreterRef.DetectedPath -> PyEvoWidgetCollector.RefKind.DETECTED_PATH
  is PyInterpreterRef.CreateEnv -> PyEvoWidgetCollector.RefKind.CREATE_ENV
  is PyInterpreterRef.Autoconfigure -> PyEvoWidgetCollector.RefKind.AUTOCONFIGURE
}

/** True when this selector adopts an environment that already exists, rather than creating a new one. */
@ApiStatus.Internal
fun PyInterpreterRef.evoReusesExistingEnv(): Boolean = when (this) {
  is PyInterpreterRef.ExistingSdk, is PyInterpreterRef.DetectedPath -> true
  // A created env is new by definition; an autoconfigure option may go either way, and the pessimistic answer keeps
  // "previously configured" from over-counting.
  is PyInterpreterRef.CreateEnv, is PyInterpreterRef.Autoconfigure -> false
}
