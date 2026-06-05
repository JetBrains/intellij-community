// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pytools

import com.intellij.openapi.util.JDOMUtil
import com.intellij.python.pytools.PyToolsState
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.util.xmlb.XmlSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Regression for `IllegalAccessException: module java.base does not open java.nio.file`
 * thrown from `KotlinAwareBeanBinding.resolveInstantiator` when XMLB treats
 * `ToolEntry.customToolBinaryPath: Path?` as a nested bean field.
 *
 * The `Path?` is serialized through `PathConverter` wired via
 * `@OptionTag(converter = ...)` so XMLB never reflects into `java.nio.file.Path`.
 * The on-disk tag name is preserved as `customPathToExecutable` for compatibility.
 */
internal class PyToolsStateSerializationTest {
  @Test
  fun `round-trips State containing a ToolEntry with a non-blank custom path`() {
    val original = PyToolsState.State(
      tools = mutableMapOf(
        "ruff" to PyToolsState.ToolEntry(
          enabled = true,
          discoveryMode = ExecutableDiscoveryMode.PATH,
          customToolBinaryPath = Path.of("/usr/local/bin/ruff"),
        ),
      ),
    )

    val element = XmlSerializer.serialize(original)
    val restored = XmlSerializer.deserialize(element, PyToolsState.State::class.java)

    assertEquals(original, restored)
    assertEquals(Path.of("/usr/local/bin/ruff"), restored.tools["ruff"]!!.customToolBinaryPath)
  }

  @Test
  fun `on-disk tag name is customPathToExecutable for back-compat`() {
    val state = PyToolsState.State(
      tools = mutableMapOf(
        "ruff" to PyToolsState.ToolEntry(customToolBinaryPath = Path.of("/usr/local/bin/ruff")),
      ),
    )

    val xml = JDOMUtil.writeElement(XmlSerializer.serialize(state))

    assertTrue(xml.contains("customPathToExecutable"), "tag name must stay as customPathToExecutable; got: $xml")
  }

  @Test
  fun `deserializes an empty State without throwing IllegalAccessException for java_nio_file`() {
    val element = XmlSerializer.serialize(PyToolsState.State())
    XmlSerializer.deserialize(element, PyToolsState.State::class.java)
  }
}
