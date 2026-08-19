// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pytools

import com.intellij.openapi.util.JDOMUtil
import com.intellij.python.pytools.PyToolsState
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.icons.PythonPyToolsIcons
import com.intellij.configurationStore.serialize
import com.jetbrains.python.packaging.PyPackageName
import com.intellij.util.xmlb.XmlSerializer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Serialization guards for [PyToolsState.State] / [PyToolsState.ToolEntry].
 *
 * [PyToolsState.ToolEntry] used to carry `discoveryMode` and a `customPathToExecutable` path; both were
 * removed once discovery became fixed and custom paths moved to the per-Eel `PyCustomExecutablePaths`.
 * XMLB skips `<option>`s that no longer map to a field, so old on-disk configs must keep loading.
 */
internal class PyToolsStateSerializationTest {
  @Test
  fun `ignores removed legacy options (discoveryMode, customPathToExecutable) from old configs`() {
    // An old pyLspTools.xml has per-tool discoveryMode + customPathToExecutable options that no longer
    // exist on ToolEntry. XMLB must skip unknown options rather than throw, so the config still loads.
    val current = JDOMUtil.writeElement(
      XmlSerializer.serialize(
        PyToolsState.State(tools = mutableMapOf("ruff" to PyToolsState.ToolEntry(enabled = true)))
      )
    )
    val legacy = current.replace(
      """<option name="enabled" value="true" />""",
      """<option name="enabled" value="true" />""" +
      """<option name="discoveryMode" value="PATH" />""" +
      """<option name="customPathToExecutable" value="/usr/local/bin/ruff" />""",
    )
    assertTrue(legacy.contains("discoveryMode"), "test setup: legacy options must be injected; got: $current")

    val restored = XmlSerializer.deserialize(JDOMUtil.load(legacy), PyToolsState.State::class.java)
    assertEquals(true, restored.tools["ruff"]?.enabled)
  }

  @Test
  fun `deserializes an empty State without throwing IllegalAccessException for java_nio_file`() {
    val element = XmlSerializer.serialize(PyToolsState.State())
    XmlSerializer.deserialize(element, PyToolsState.State::class.java)
  }

  /**
   * The `.idea/pyLspTools.xml` file is written by the component store only while [PyToolsState.State]
   * serializes to non-empty content. [com.intellij.configurationStore.serialize] (the same entry point the
   * store uses, with the default skip-defaults filter) returns `null` for an all-default bean, which is exactly
   * the "no file on disk" condition. Driving the change through [PyToolsState.State.persist] also covers the
   * remove-on-default path that makes the file disappear again once every tool is back to its defaults.
   */
  @Test
  fun `state has no storage content at defaults, gains it on change, and loses it again on revert`() {
    val state = PyToolsState.State()
    val tool = object : PyTool {
      override val presentableName: String = "ruff"
      override val packageName: PyPackageName = PyPackageName.from("ruff")
      override val description: String = "ruff"
      override val icon = PythonPyToolsIcons.Logo
    }

    // All tools at their defaults -> nothing to persist -> no .idea/pyLspTools.xml.
    assertNull(serialize(state), "a default state must serialize to nothing (no storage file)")

    // Change a setting -> the entry is persisted -> the file is written and holds the settings.
    state.persist(tool, PyToolsState.ToolEntry(enabled = true))
    val element = serialize(state)
    assertNotNull(element, "a tool that differs from defaults must produce storage content")
    val xml = JDOMUtil.writeElement(element!!)
    assertTrue(xml.contains("ruff"), "storage must reference the changed tool; got: $xml")
    assertTrue(xml.contains("enabled"), "storage must contain the changed setting; got: $xml")

    // Revert every setting to its default -> the entry is dropped -> the file is deleted.
    state.persist(tool, PyToolsState.ToolEntry())
    assertNull(serialize(state), "reverting all tools to defaults must serialize to nothing (storage file deleted)")
  }
}
