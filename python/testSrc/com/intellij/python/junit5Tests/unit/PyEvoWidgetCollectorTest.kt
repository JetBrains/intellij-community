// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.python.sdk.common.evolution.EvoNodeKind
import com.intellij.python.sdk.common.evolution.EvoNodeStats
import com.intellij.python.sdk.common.evolution.PyEvoWidgetCollector
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.intellij.python.sdk.common.evolution.evoRefKind
import com.intellij.python.sdk.common.evolution.evoReusesExistingEnv
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

private const val GROUP_ID: String = "python.sdk.widget"

/**
 * Guards the rule that ties [EvoNodeStats] to what reaches the wire: a tool name is reported for a tool-backed node
 * and for nothing else, so "this node has no tool" and "we could not name the tool" stay distinguishable.
 *
 * Enum fields serialize via `EventFields.defaultEnumTransform` (`toString()`), so constant names reach the wire
 * verbatim — uppercase.
 */
@TestApplication
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PyEvoWidgetCollectorTest {
  private fun logged(disposable: Disposable, eventId: String, body: () -> Unit): Map<String, Any> =
    FUCollectorTestCase.collectLogEvents(disposable) { body() }
      .single { it.group.id == GROUP_ID && it.event.id == eventId }
      .event.data

  @Test
  fun `a tool node reports its kind and the tool's fusId`(@TestDisposable disposable: Disposable) {
    val data = logged(disposable, "node.expanded") {
      PyEvoWidgetCollector.nodeExpanded(
        project = null,
        node = EvoNodeStats(EvoNodeKind.TOOL, "conda"),
        outcome = PyEvoWidgetCollector.NodeOutcome.EMPTY,
        isReload = true,
        wasSlow = true,
        durationMs = 1234,
      )
    }

    assertEquals("TOOL", data["node_kind"])
    // The tool's own FUS identity, reported verbatim — this collector keeps no vocabulary of its own.
    assertEquals("conda", data["tool_name"])
    // The plain-virtualenv node has no PyTool but is still a tool here, naming itself `pip`, so tool popularity is
    // one query over one field rather than a union with a separate kind.
    assertEquals("pip", EvoNodeStats(EvoNodeKind.TOOL, "pip").fusId)
    assertEquals("EMPTY", data["node_outcome"])
    assertEquals(true, data["is_reload"])
    assertEquals(true, data["was_slow"])
    assertEquals(1234L, data["duration_ms"])
  }

  @Test
  fun `a node with no tool behind it reports no tool name`(@TestDisposable disposable: Disposable) {
    // One collect for all three: FUCollectorTestCase masks the logger extension point, which cannot be re-masked
    // within a single test.
    val kinds = listOf(EvoNodeKind.ADVANCED, EvoNodeKind.OTHER)
    val events = FUCollectorTestCase.collectLogEvents(disposable) {
      for (kind in kinds) {
        PyEvoWidgetCollector.nodeExpanded(null, EvoNodeStats(kind), PyEvoWidgetCollector.NodeOutcome.OK, false, false, 1)
      }
    }.filter { it.group.id == GROUP_ID && it.event.id == "node.expanded" }

    assertEquals(kinds.size, events.size)
    for (kind in kinds) {
      val data = events.single { it.event.data["node_kind"] == kind.name }.event.data
      assertNull(data["tool_name"], "$kind must not report a tool name")
    }
  }

  @Test
  fun `a tool name supplied for a non-tool kind is dropped, not reported`(@TestDisposable disposable: Disposable) {
    // The pairing is a rule, not a convention: a stray fusId on a structural section would invent a tool the node does
    // not have, and in a report it would be indistinguishable from a real one.
    val data = logged(disposable, "node.expanded") {
      PyEvoWidgetCollector.nodeExpanded(
        null, EvoNodeStats(EvoNodeKind.ADVANCED, "uv"), PyEvoWidgetCollector.NodeOutcome.OK, false, false, 1)
    }
    assertEquals("ADVANCED", data["node_kind"])
    assertNull(data["tool_name"])
  }

  @Test
  fun `ref kind mirrors the selector`() {
    assertEquals(PyEvoWidgetCollector.RefKind.EXISTING_SDK, PyInterpreterRef.ExistingSdk("sdk").evoRefKind())
    assertEquals(PyEvoWidgetCollector.RefKind.DETECTED_PATH, PyInterpreterRef.DetectedPath("/x").evoRefKind())
    assertEquals(PyEvoWidgetCollector.RefKind.CREATE_ENV, PyInterpreterRef.CreateEnv("3.12").evoRefKind())
    assertEquals(PyEvoWidgetCollector.RefKind.AUTOCONFIGURE, PyInterpreterRef.Autoconfigure("Venv").evoRefKind())
  }

  @Test
  fun `only refs that adopt an existing environment count as previously configured`() {
    assertTrue(PyInterpreterRef.ExistingSdk("sdk").evoReusesExistingEnv())
    assertTrue(PyInterpreterRef.DetectedPath("/x").evoReusesExistingEnv())
    assertFalse(PyInterpreterRef.CreateEnv("3.12").evoReusesExistingEnv())
    assertFalse(PyInterpreterRef.Autoconfigure("Venv").evoReusesExistingEnv())
  }

  @Test
  fun `a selection carries the node identity, the ref kind and the section`(@TestDisposable disposable: Disposable) {
    val data = logged(disposable, "interpreter.selected") {
      PyEvoWidgetCollector.interpreterSelected(
        project = null,
        node = EvoNodeStats(EvoNodeKind.TOOL, "uv"),
        refKind = PyEvoWidgetCollector.RefKind.CREATE_ENV,
        source = PyEvoWidgetCollector.Source.ADD_NEW_VERSION,
      )
    }
    assertEquals("TOOL", data["node_kind"])
    assertEquals("uv", data["tool_name"])
    assertEquals("CREATE_ENV", data["ref_kind"])
    assertEquals("ADD_NEW_VERSION", data["source"])
  }

  @Test
  fun `a shortcuts row is a section, not a tool`(@TestDisposable disposable: Disposable) {
    // An autoconfigure option is keyed by a PyProjectSdkConfigurationExtension tool id, a different vocabulary from
    // PyExecutable.fusId — so it is reported as the section it is, and no tool name is invented for it.
    val data = logged(disposable, "interpreter.selected") {
      PyEvoWidgetCollector.interpreterSelected(
        null, EvoNodeStats(EvoNodeKind.SHORTCUTS), PyEvoWidgetCollector.RefKind.AUTOCONFIGURE,
        PyEvoWidgetCollector.Source.SHORTCUTS)
    }
    assertEquals("SHORTCUTS", data["node_kind"])
    assertNull(data["tool_name"])
  }

  @Test
  fun `a static section reports its opening without the scan fields`(@TestDisposable disposable: Disposable) {
    // The Associated node is built from already-fetched SDKs, so there is no scan to time. Absent scan fields are the
    // marker for that — a zero duration would drag the tool-scan timings this event also carries toward zero.
    val data = logged(disposable, "node.expanded") {
      PyEvoWidgetCollector.staticNodeOpened(null, EvoNodeStats(EvoNodeKind.ASSOCIATED))
    }
    assertEquals("ASSOCIATED", data["node_kind"])
    assertEquals("OK", data["node_outcome"])
    assertNull(data["duration_ms"])
    assertNull(data["is_reload"])
    assertNull(data["was_slow"])
  }

  @Test
  fun `a control that belongs to no node names no tool`(@TestDisposable disposable: Disposable) {
    val events = FUCollectorTestCase.collectLogEvents(disposable) {
      PyEvoWidgetCollector.controlUsed(
        project = null,
        control = PyEvoWidgetCollector.Control.BASE_PYTHON_PANEL,
        node = EvoNodeStats(EvoNodeKind.TOOL, "uv"),
      )
      PyEvoWidgetCollector.controlUsed(project = null, control = PyEvoWidgetCollector.Control.GEAR_SETTINGS)
    }

    val used = events.filter { it.group.id == GROUP_ID && it.event.id == "control.used" }
    assertEquals(2, used.size)

    val rebuild = used.single { it.event.data["control"] == "BASE_PYTHON_PANEL" }.event.data
    assertEquals("uv", rebuild["tool_name"])

    // A header-level control belongs to no node, so it defaults to OTHER and names no tool.
    val gear = used.single { it.event.data["control"] == "GEAR_SETTINGS" }.event.data
    assertEquals("OTHER", gear["node_kind"])
    assertNull(gear["tool_name"])
  }

  @Test
  fun `a failed advanced action is reported, not dropped`(@TestDisposable disposable: Disposable) {
    val data = logged(disposable, "backend.action.performed") {
      PyEvoWidgetCollector.backendActionPerformed(
        null, EvoNodeStats(EvoNodeKind.ADVANCED), PyEvoWidgetCollector.Outcome.ERROR)
    }
    assertEquals("ADVANCED", data["node_kind"])
    assertEquals("ERROR", data["outcome"])
    assertNull(data["tool_name"])
  }
}
