// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.openapi.util.JDOMUtil
import com.jetbrains.python.console.PyConsoleOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Regression coverage for [PY-90067](https://youtrack.jetbrains.com/issue/PY-90067).
 *
 * `PyConsoleSettings` used to serialize its `sdk` property, whose type [com.intellij.openapi.projectRoots.Sdk]
 * is an interface. When a user had configured a console interpreter, the resolved `Sdk` object was written to
 * `workspace.xml`; on the next load the serializer tried to instantiate the `Sdk` interface and failed
 * (`NoSuchElementException: List is empty` from `createUsingKotlin`, i.e. `Sdk` has no constructors).
 *
 * The `sdk` object is transient runtime state — the persistent reference is the `sdk-home` path
 * ([PyConsoleOptions.PyConsoleSettings.getSdkHome]). It is now `@Transient`, so it is neither written nor read.
 */
class PyConsoleOptionsSerializationTest {

  @Test
  fun `PyConsoleSettings round-trips through XmlSerializer`() {
    val original = PyConsoleOptions.PyConsoleSettings().apply {
      myCustomStartScript = "import sys"
      myInterpreterOptions = "-v -O"
      myWorkingDirectory = "/tmp/test"
      myEnvs = mutableMapOf("KEY" to "VALUE")
      myUseModuleSdk = true
      myModuleName = "my-module"
      myAddContentRoots = false
      myAddSourceRoots = false
      myPassParentEnvs = false
    }

    val element = serialize(original)
    assertNotNull(element)

    val restored = element!!.deserialize(PyConsoleOptions.PyConsoleSettings::class.java)
    assertEquals("import sys", restored.myCustomStartScript)
    assertEquals("-v -O", restored.myInterpreterOptions)
    assertEquals("/tmp/test", restored.myWorkingDirectory)
    assertEquals(mapOf("KEY" to "VALUE"), restored.myEnvs)
    assertEquals(true, restored.myUseModuleSdk)
    assertEquals("my-module", restored.myModuleName)
    assertEquals(false, restored.myAddContentRoots)
    assertEquals(false, restored.myAddSourceRoots)
    assertEquals(false, restored.myPassParentEnvs)
  }

  @Test
  fun `State round-trips and preserves nested PyConsoleSettings`() {
    val original = PyConsoleOptions.State().apply {
      myShowDebugConsoleByDefault = false
      myShowVariablesByDefault = false
      myIpythonEnabled = false
      myUseExistingConsole = true
      myCommandQueueEnabled = true
      myPythonConsoleState = PyConsoleOptions.PyConsoleSettings().apply {
        myCustomStartScript = "print('hi')"
        myWorkingDirectory = "/work"
      }
    }

    val element = serialize(original)
    assertNotNull(element)

    val restored = element!!.deserialize(PyConsoleOptions.State::class.java)
    assertEquals(false, restored.myShowDebugConsoleByDefault)
    assertEquals(false, restored.myShowVariablesByDefault)
    assertEquals(false, restored.myIpythonEnabled)
    assertEquals(true, restored.myUseExistingConsole)
    assertEquals(true, restored.myCommandQueueEnabled)
    assertEquals("print('hi')", restored.myPythonConsoleState.myCustomStartScript)
    assertEquals("/work", restored.myPythonConsoleState.myWorkingDirectory)
  }

  /**
   * The `sdk` property (type `Sdk`, an interface) must not be serialized: an interface cannot be instantiated
   * during deserialization. Serializing settings that hold a resolved SDK must not emit any `sdk` element.
   */
  @Test
  fun `resolved sdk object is not serialized`() {
    val element = serialize(PyConsoleOptions.PyConsoleSettings())
    val xml = element?.let { JDOMUtil.write(it) } ?: ""
    assert(!xml.contains("name=\"sdk\"") && !xml.contains("name=\"mySdk\"")) {
      "PyConsoleSettings must not serialize the resolved Sdk object, but got:\n$xml"
    }
  }

  /**
   * The actual PY-90067 reproduction: a `workspace.xml` written when a console interpreter was configured
   * contains a persisted `sdk`/`mySdk` element. Loading it must not fail trying to instantiate the `Sdk`
   * interface; the stale element is ignored and the remaining settings load normally.
   */
  @Test
  fun `legacy console-settings with persisted sdk element loads without failure`() {
    val legacy = JDOMUtil.load(
      """
      <console-settings working-directory="/work" custom-start-script="import sys">
        <option name="sdk">
          <value />
        </option>
        <option name="mySdk">
          <value />
        </option>
      </console-settings>
      """.trimIndent()
    )

    val restored = legacy.deserialize(PyConsoleOptions.PyConsoleSettings::class.java)
    assertEquals("/work", restored.myWorkingDirectory)
    assertEquals("import sys", restored.myCustomStartScript)
  }
}
