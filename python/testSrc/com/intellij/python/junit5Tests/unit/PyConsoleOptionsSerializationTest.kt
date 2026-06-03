// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.jetbrains.python.console.PyConsoleOptions
import org.jdom.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Regression coverage for [PY-90067](https://youtrack.jetbrains.com/issue/PY-90067).
 *
 * Deserialization of `PyConsoleOptions.State` (and the nested `PyConsoleSettings` with `@Tag("console-settings")`)
 * goes through [com.intellij.serialization.xml.KotlinAwareBeanBinding]. For a Java [PyConsoleSettings] that
 * path could throw `NoSuchElementException` from `createUsingKotlin` when Kotlin reflection saw an empty
 * constructor list. Keeping the class Kotlin (with a primary constructor) ensures the fallback uses the
 * primary constructor and never reaches the empty-list throw.
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
   * Direct deserialization from an empty `<console-settings/>` element exercises the
   * `KotlinAwareBeanBinding.newInstance` path for [PyConsoleOptions.PyConsoleSettings] —
   * the exact spot where PY-90067 used to throw `NoSuchElementException`.
   */
  @Test
  fun `empty console-settings element deserializes without NoSuchElementException`() {
    val empty = Element("console-settings")
    val settings = empty.deserialize(PyConsoleOptions.PyConsoleSettings::class.java)
    // Defaults survive: just verify the instance was constructed.
    assertEquals("", settings.myWorkingDirectory)
    assertEquals(true, settings.myAddContentRoots)
  }
}
