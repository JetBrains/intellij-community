// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyWorkspaceStructureModel
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.SdkName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Full-behavior tests for [PyWorkspaceStructureModel] — decisions, pool-sync, and the refresh
 * signal. The model works on [SdkName]s only, so no `Sdk` fixture is needed.
 */
internal class PyWorkspaceStructureModelTest {

  @Test
  fun `jdkAdded syncs the pool when the sdk name is new`() {
    val table = FakeSdksTable(names = mutableSetOf(SdkName("Python 3.11")))
    var refreshes = 0
    val model = PyWorkspaceStructureModel(table) { refreshes++ }

    model.onJdkAdded(SdkName("Python 3.12"))

    assertEquals(1, table.syncs)
    assertEquals(1, refreshes)
  }

  @Test
  fun `jdkAdded is a no-op when the sdk name is already tracked`() {
    val table = FakeSdksTable(names = mutableSetOf(SdkName("Python 3.12")))
    var refreshes = 0
    val model = PyWorkspaceStructureModel(table) { refreshes++ }

    model.onJdkAdded(SdkName("Python 3.12"))

    assertEquals(0, table.syncs)
    assertEquals(1, refreshes)
  }

  @Test
  fun `jdkRemoved syncs the pool when the sdk name is tracked`() {
    val table = FakeSdksTable(names = mutableSetOf(SdkName("Python 3.12")))
    var refreshes = 0
    val model = PyWorkspaceStructureModel(table) { refreshes++ }

    model.onJdkRemoved(SdkName("Python 3.12"))

    assertEquals(1, table.syncs)
    assertEquals(1, refreshes)
  }

  @Test
  fun `jdkRemoved is a no-op when the sdk name is absent`() {
    val table = FakeSdksTable(names = mutableSetOf(SdkName("Python 3.11")))
    var refreshes = 0
    val model = PyWorkspaceStructureModel(table) { refreshes++ }

    model.onJdkRemoved(SdkName("Python 3.12"))

    assertEquals(0, table.syncs)
    assertEquals(1, refreshes)
  }

  @Test
  fun `jdkRenamed refreshes without touching the pool`() {
    val table = FakeSdksTable(names = mutableSetOf(SdkName("Python 3.12")))
    var refreshes = 0
    val model = PyWorkspaceStructureModel(table) { refreshes++ }

    model.onJdkRenamed()

    assertEquals(0, table.syncs)
    assertEquals(1, refreshes)
  }

  //region test fixtures

  private class FakeSdksTable(val names: MutableSet<SdkName>) : PyWorkspaceStructureModel.SdksTable {
    var syncs: Int = 0

    override fun trackedNames(): Set<SdkName> = names.toSet()

    override fun syncFromJdkTable() {
      syncs++
    }
  }

  //endregion
}
