// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyModuleDetailsModel
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.SdkComboContents
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-state tests for [PyModuleDetailsModel]. Comparing by SDK name reflects the module editor's
 * apply semantics — the editable clone from `ProjectSdksModel` and the registered SDK differ by
 * instance identity but share a name.
 */
internal class PyModuleDetailsModelTest {

  //region isSdkChanged / markSdkApplied

  @Test
  fun `no change when nothing was selected`() {
    val model = PyModuleDetailsModel(initialSdkName = "Python 3.12")
    assertFalse(model.isSdkChanged("Python 3.12"))
    assertEquals("Python 3.12", model.initialSdkName)
  }

  @Test
  fun `change detected when a different name is chosen`() {
    val model = PyModuleDetailsModel(initialSdkName = "Python 3.12")
    assertTrue(model.isSdkChanged("Python 3.11"))
  }

  @Test
  fun `change detected when the module goes from no SDK to a chosen SDK`() {
    val model = PyModuleDetailsModel(initialSdkName = null)
    assertTrue(model.isSdkChanged("Python 3.12"))
  }

  @Test
  fun `change detected when the module SDK is cleared`() {
    val model = PyModuleDetailsModel(initialSdkName = "Python 3.12")
    assertTrue(model.isSdkChanged(null))
  }

  @Test
  fun `markSdkApplied resets the baseline`() {
    val model = PyModuleDetailsModel(initialSdkName = "Python 3.12")
    model.markSdkApplied("Python 3.11")
    assertEquals("Python 3.11", model.initialSdkName)
    assertFalse(model.isSdkChanged("Python 3.11"))
    assertTrue(model.isSdkChanged("Python 3.12"))
  }

  @Test
  fun `markSdkApplied accepts null when the caller clears the SDK`() {
    val model = PyModuleDetailsModel(initialSdkName = "Python 3.12")
    model.markSdkApplied(null)
    assertNull(model.initialSdkName)
    assertFalse(model.isSdkChanged(null))
  }

  //endregion

  //region buildComboContents

  @Test
  fun `combo preserves the caller order`() {
    val model = PyModuleDetailsModel(initialSdkName = null)
    val contents = model.buildComboContents(
      pythonSdkNames = listOf("Python 3.10", "Python 3.11", "Python 3.12"),
      currentModuleSdkName = null,
    )
    assertEquals(
      SdkComboContents(
        orderedSdkNames = listOf("Python 3.10", "Python 3.11", "Python 3.12"),
        includeNoInterpreter = false,
      ),
      contents,
    )
  }

  @Test
  fun `combo prepends the module SDK when the association filter stripped it`() {
    val model = PyModuleDetailsModel(initialSdkName = "shared")
    val contents = model.buildComboContents(
      pythonSdkNames = listOf("Python 3.11"),
      currentModuleSdkName = "shared",
    )
    assertEquals(listOf("shared", "Python 3.11"), contents.orderedSdkNames)
    assertFalse(contents.includeNoInterpreter)
  }

  @Test
  fun `combo does not duplicate the module SDK when it is already listed`() {
    val model = PyModuleDetailsModel(initialSdkName = "Python 3.12")
    val contents = model.buildComboContents(
      pythonSdkNames = listOf("Python 3.11", "Python 3.12"),
      currentModuleSdkName = "Python 3.12",
    )
    assertEquals(listOf("Python 3.11", "Python 3.12"), contents.orderedSdkNames)
    assertFalse(contents.includeNoInterpreter)
  }

  @Test
  fun `combo shows the no-interpreter placeholder only when the SDK list is empty`() {
    val model = PyModuleDetailsModel(initialSdkName = null)
    val contents = model.buildComboContents(
      pythonSdkNames = emptyList(),
      currentModuleSdkName = null,
    )
    assertTrue(contents.orderedSdkNames.isEmpty())
    assertTrue(contents.includeNoInterpreter)
  }

  //endregion
}
