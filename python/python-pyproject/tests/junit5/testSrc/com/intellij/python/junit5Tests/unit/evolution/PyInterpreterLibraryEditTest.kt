// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.evolution

import com.intellij.python.pyproject.model.evolution.InterpreterLibraryPaths
import com.intellij.python.pyproject.model.evolution.factsAfterLibraryEdit
import com.intellij.python.sdk.backend.PythonInterpreterFacts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * An edit of an interpreter library on the Libraries page of a Java IDE goes into the facts of its interpreter, as the
 * paths dialog of PyCharm writes them. The library is then built again from the facts.
 */
internal class PyInterpreterLibraryEditTest {
  private val stdlib = Path.of("/py/lib")
  private val sitePackages = Path.of("/py/lib/site-packages")
  private val extra = Path.of("/extra")
  private val tests = Path.of("/py/lib/test")

  private val facts = PythonInterpreterFacts(version = "Python 3.14", sysPath = listOf(stdlib, sitePackages))
  private val written = InterpreterLibraryPaths(classRoots = listOf(stdlib, sitePackages), excludedFolders = emptySet())

  @Test
  fun `an edit that changes nothing keeps the facts`() {
    assertThat(factsAfterLibraryEdit(facts, written, written)).isEqualTo(facts)
  }

  @Test
  fun `an added root becomes an added path`() {
    val edited = written.copy(classRoots = written.classRoots + listOf(extra))
    assertThat(factsAfterLibraryEdit(facts, written, edited).addedPaths).containsExactly(extra)
  }

  @Test
  fun `a removed root of sys path becomes an excluded path`() {
    val edited = written.copy(classRoots = listOf(stdlib))
    assertThat(factsAfterLibraryEdit(facts, written, edited).excludedPaths).containsExactly(sitePackages)
  }

  @Test
  fun `a removed added root leaves the added paths`() {
    val withExtra = facts.copy(addedPaths = listOf(extra))
    val writtenWithExtra = written.copy(classRoots = written.classRoots + listOf(extra))
    val after = factsAfterLibraryEdit(withExtra, writtenWithExtra, written)
    assertThat(after.addedPaths).isEmpty()
    assertThat(after.excludedPaths).isEmpty()
  }

  @Test
  fun `an excluded root that is added again leaves the excluded paths`() {
    val withExcluded = facts.copy(excludedPaths = setOf(sitePackages))
    val writtenWithout = written.copy(classRoots = listOf(stdlib))
    val after = factsAfterLibraryEdit(withExcluded, writtenWithout, written)
    assertThat(after.excludedPaths).isEmpty()
    assertThat(after.addedPaths).isEmpty()
  }

  @Test
  fun `an excluded folder becomes an excluded folder of the interpreter`() {
    val edited = written.copy(excludedFolders = setOf(tests))
    assertThat(factsAfterLibraryEdit(facts, written, edited).excludedFolders).containsExactly(tests)
  }

  @Test
  fun `a folder that is no longer excluded leaves the excluded folders`() {
    val withFolder = facts.copy(excludedFolders = setOf(tests))
    val writtenWithFolder = written.copy(excludedFolders = setOf(tests))
    assertThat(factsAfterLibraryEdit(withFolder, writtenWithFolder, written).excludedFolders).isEmpty()
  }
}
