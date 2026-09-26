// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PySourcesModel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * State-transition tests for [PySourcesModel] — the wrapper the settings pane owns instead of
 * poking `ModuleRootManager.modifiableModel` from the EDT. Exercises the coroutine surface with
 * a real project + module fixture; the sources-editor itself has no assertions here beyond
 * "was built for this module", the deeper root-editing behaviour lives in the Swing pane and
 * needs `ContentEntry` fixtures beyond what this JUnit 5 unit module carries.
 */
@TestApplication
class PySourcesModelTest {

  companion object {
    private val projectFixture = projectFixture()
    private val moduleFixture = projectFixture.moduleFixture()
  }

  private val project get() = projectFixture.get()
  private val module get() = moduleFixture.get()

  @Test
  fun `moduleProject returns the module's owning project`() {
    val model = PySourcesModel(module)
    assertSame(project, model.moduleProject())
  }

  @Test
  fun `state is empty before createEditor is called`() {
    val model = PySourcesModel(module)
    assertNull(model.currentEditor())
    assertFalse(model.isModified())
  }

  @Test
  fun `apply is a no-op when no editor exists yet`() = runBlocking {
    val model = PySourcesModel(module)
    assertFalse(model.apply())
  }

  @Test
  fun `createEditor produces a fresh editor cached on the model and reports unmodified`() = runBlocking {
    val model = PySourcesModel(module)
    val editor = model.createEditor()
    assertNotNull(editor)
    assertSame(editor, model.currentEditor())
    assertFalse(model.isModified())
    assertEquals(false, model.apply())
    model.disposeEditor()
    model.disposeModel()
  }

  @Test
  fun `disposeEditor clears the cached editor so a subsequent createEditor builds a fresh one`() = runBlocking {
    val model = PySourcesModel(module)
    val first = model.createEditor()
    model.disposeEditor()
    assertNull(model.currentEditor())
    val second = model.createEditor()
    assertNotNull(second)
    // Different instances — the pane relies on `disposeEditor` to drop the previous Swing tree.
    assert(first !== second)
    model.disposeEditor()
    model.disposeModel()
  }
}
