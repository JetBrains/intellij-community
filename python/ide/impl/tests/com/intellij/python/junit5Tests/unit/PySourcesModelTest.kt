// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PySourcesModel
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Session-level tests for [PySourcesModel]. The model is now a plain factory, so the per-session
 * behaviour (edit flag, apply-and-commit, dispose) is exercised against the real project + module
 * fixture; the sources-editor itself has no deeper assertions here beyond "a session was built",
 * the full root-editing behaviour lives in the Swing pane and needs `ContentEntry` fixtures beyond
 * what this JUnit 5 unit module carries.
 */
@TestApplication
class PySourcesModelTest {

  companion object {
    private val projectFixture = projectFixture()
    private val pyProjectFixture = projectFixture.pyProjectFixture()
  }

  private val project get() = projectFixture.get()
  private val pyProject get() = pyProjectFixture.get()

  @Test
  fun `project returns the pyproject's owning project`() {
    val model = PySourcesModel(pyProject)
    assertSame(project, model.project())
  }

  @Test
  fun `createSession produces a fresh session that reports unmodified and no-op applies`() = runBlocking {
    val session = PySourcesModel(pyProject).createSession()
    assertNotNull(session)
    assertFalse(session.isModified)
    assertFalse(session.applyAndCommit())
    session.disposeAsync()
  }

  @Test
  fun `createSession twice yields distinct sessions so each caller can dispose independently`() = runBlocking {
    val model = PySourcesModel(pyProject)
    val first = model.createSession()
    val second = model.createSession()
    assert(first !== second)
    first.disposeAsync()
    second.disposeAsync()
  }
}
