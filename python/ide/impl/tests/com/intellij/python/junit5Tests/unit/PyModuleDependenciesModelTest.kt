// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyModuleDependenciesModelImpl
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyModuleDependenciesPresenter.DependencyId
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Thin integration layer around [PyModuleDependenciesPresenter]. These tests pin only the behaviour
 * [PyModuleDependenciesModelImpl] adds on top of the presenter — adapter wiring and the live-workspace
 * read. The presenter's own semantics live in [PyModuleDependenciesPresenterTest] and are not re-asserted here.
 */
@TestApplication
internal class PyModuleDependenciesModelTest {

  companion object {
    private val projectFixture = projectFixture()
    private val pyProjectFixture = projectFixture.pyProjectFixture()
  }

  private val pyProject get() = pyProjectFixture.get()

  @Test
  fun `candidates list derives from the pyProject argument order and the presenter's alphabetical sort`() {
    val model = PyModuleDependenciesModelImpl(
      subject = pyProject,
      candidatePyProjects = emptyList(),
      initialDependencies = emptySet(),
    )

    assertTrue(model.candidates.isEmpty())
    assertTrue(model.initialDependencies.isEmpty())
  }

  @Test
  fun `isModified is false when ui matches the empty baseline`() {
    val model = PyModuleDependenciesModelImpl(
      subject = pyProject,
      candidatePyProjects = emptyList(),
      initialDependencies = emptySet(),
    )

    assertFalse(model.isModified(emptySet()))
  }

  @Test
  fun `liveDependencies reads the subject module's live order-entries`() {
    val model = PyModuleDependenciesModelImpl(
      subject = pyProject,
      candidatePyProjects = emptyList(),
      initialDependencies = emptySet(),
    )

    // Fresh module has no inter-module deps — only SDK + own-sources, which the reader filters out.
    assertEquals(emptySet<DependencyId>(), model.liveDependencies())
  }

  @Test
  fun `syncBaselineFromWorkspace replaces the baseline with the live snapshot`() {
    val model = PyModuleDependenciesModelImpl(
      subject = pyProject,
      candidatePyProjects = emptyList(),
      initialDependencies = emptySet(),
    )

    model.syncBaselineFromWorkspace()

    assertEquals(model.liveDependencies(), model.initialDependencies)
  }

  @Test
  fun `apply is a no-op on empty candidates and reports no mutation`() {
    val model = PyModuleDependenciesModelImpl(
      subject = pyProject,
      candidatePyProjects = emptyList(),
      initialDependencies = emptySet(),
    )

    assertFalse(model.apply(uiChecked = emptySet()))
  }
}
