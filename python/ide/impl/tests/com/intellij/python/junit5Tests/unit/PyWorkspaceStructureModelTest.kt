// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyJdkHandle
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyWorkspaceStructureModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Full-behavior tests for [PyWorkspaceStructureModel] — decisions, mutations, and the refresh
 * signal. Handles are built via [PyJdkHandle.forName] so no `Sdk` fixture is needed; the platform
 * `Sdk` interface is non-extensible and reflection-based stubs are unreliable on CI.
 */
internal class PyWorkspaceStructureModelTest {

  @Test
  fun `jdkAdded inserts the sdk when its name is new`() {
    val table = FakeSdksTable(names = mutableSetOf("Python 3.11"))
    var refreshes = 0
    val model = PyWorkspaceStructureModel(table) { refreshes++ }

    model.onJdkAdded(PyJdkHandle.forName("Python 3.12"))

    assertEquals(listOf("Python 3.12"), table.added)
    assertEquals(setOf("Python 3.11", "Python 3.12"), table.names)
    assertEquals(1, refreshes)
  }

  @Test
  fun `jdkAdded is a no-op when the name is already tracked`() {
    val table = FakeSdksTable(names = mutableSetOf("Python 3.12"))
    var refreshes = 0
    val model = PyWorkspaceStructureModel(table) { refreshes++ }

    model.onJdkAdded(PyJdkHandle.forName("Python 3.12"))

    assertTrue(table.added.isEmpty())
    assertEquals(1, refreshes)
  }

  @Test
  fun `jdkRemoved drops the sdk when its name is tracked`() {
    val table = FakeSdksTable(names = mutableSetOf("Python 3.12"))
    var refreshes = 0
    val model = PyWorkspaceStructureModel(table) { refreshes++ }

    model.onJdkRemoved(PyJdkHandle.forName("Python 3.12"))

    assertEquals(listOf("Python 3.12"), table.removed)
    assertEquals(emptySet<String>(), table.names)
    assertEquals(1, refreshes)
  }

  @Test
  fun `jdkRemoved is a no-op when the name is absent`() {
    val table = FakeSdksTable(names = mutableSetOf("Python 3.11"))
    var refreshes = 0
    val model = PyWorkspaceStructureModel(table) { refreshes++ }

    model.onJdkRemoved(PyJdkHandle.forName("Python 3.12"))

    assertTrue(table.removed.isEmpty())
    assertEquals(1, refreshes)
  }

  @Test
  fun `jdkRenamed refreshes without touching the table`() {
    val table = FakeSdksTable(names = mutableSetOf("Python 3.12"))
    var refreshes = 0
    val model = PyWorkspaceStructureModel(table) { refreshes++ }

    model.onJdkRenamed()

    assertTrue(table.added.isEmpty())
    assertTrue(table.removed.isEmpty())
    assertEquals(1, refreshes)
  }

  //region test fixtures

  private class FakeSdksTable(val names: MutableSet<String>) : PyWorkspaceStructureModel.SdksTable {
    val added: MutableList<String> = mutableListOf()
    val removed: MutableList<String> = mutableListOf()

    override fun trackedNames(): Set<String> = names.toSet()

    override fun add(jdk: PyJdkHandle) {
      added.add(jdk.name)
      names.add(jdk.name)
    }

    override fun removeByName(name: String) {
      removed.add(name)
      names.remove(name)
    }
  }

  //endregion
}
