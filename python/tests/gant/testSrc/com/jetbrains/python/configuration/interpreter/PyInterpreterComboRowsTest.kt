// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration.interpreter

import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.configuration.interpreter.SdkComboItem.Existing
import com.jetbrains.python.configuration.interpreter.SdkComboItem.GroupHeader
import com.jetbrains.python.configuration.interpreter.SdkComboItem.ModuleInterpreter
import com.jetbrains.python.configuration.interpreter.SdkComboItem.ShowAll
import com.jetbrains.python.configuration.interpreter.SdkComboItem.ToggleOtherProjects
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The rows of [PyInterpreterComboBox], pinned with plain values in place of interpreters. */
@Subsystems.Interpreters
@Layers.Functional
internal class PyInterpreterComboRowsTest {
  @Test
  fun `the rows keep the order of the own interpreters`() {
    val source = InterpreterComboSource(moduleInterpreter = null, ownInterpreters = listOf("a", "b", "c"), projectInterpreters = listOf("a", "b", "c"))
    assertThat(rows(source)).containsExactly(Existing("a"), Existing("b"), Existing("c"))
  }

  @Test
  fun `the interpreter of the module comes first when the own interpreters do not hold it`() {
    val source = InterpreterComboSource(moduleInterpreter = "shared", ownInterpreters = listOf("a"), projectInterpreters = listOf("a"))
    assertThat(rows(source)).containsExactly(Existing("shared"), Existing("a"))
  }

  @Test
  fun `the interpreter of the module has one row`() {
    val source = InterpreterComboSource(moduleInterpreter = "b", ownInterpreters = listOf("a", "b"), projectInterpreters = listOf("a", "b"))
    assertThat(rows(source)).containsExactly(Existing("a"), Existing("b"))
  }

  @Test
  fun `no interpreter gives no row, the combo shows a grey text for no selection`() {
    val empty = InterpreterComboSource<String>(moduleInterpreter = null, ownInterpreters = emptyList(), projectInterpreters = emptyList())
    assertThat(rows(empty)).isEmpty()
  }

  @Test
  fun `the interpreters of the other projects show only after the toggle`() {
    val source = InterpreterComboSource(moduleInterpreter = null, ownInterpreters = listOf("a"), projectInterpreters = listOf("a", "x"))
    assertThat(rows(source)).containsExactly(Existing("a"), ToggleOtherProjects)
    assertThat(rows(source, showOtherProjects = true)).containsExactly(Existing("a"), Existing("x"), ToggleOtherProjects)
  }

  @Test
  fun `a kept selection that no project lists comes first`() {
    val source = InterpreterComboSource(moduleInterpreter = null, ownInterpreters = listOf("a"), projectInterpreters = listOf("a", "x"))
    assertThat(rows(source, showOtherProjects = true, kept = listOf("k")))
      .containsExactly(Existing("k"), Existing("a"), Existing("x"), ToggleOtherProjects)
  }

  @Test
  fun `the owned mode lists only the own interpreters, also for a kept selection of another project`() {
    val source = InterpreterComboSource(
      moduleInterpreter = null,
      ownInterpreters = listOf("a"),
      projectInterpreters = listOf("a", "x"),
      owners = mapOf("a" to "app", "x" to "lib"),
    )
    assertThat(rows(source, kept = listOf("x"))).containsExactly(Existing("a"), ToggleOtherProjects)
  }

  @Test
  fun `the owner captions show only for more than one owner, the own owner first`() {
    val source = InterpreterComboSource(
      moduleInterpreter = null,
      ownInterpreters = listOf("a"),
      projectInterpreters = listOf("x", "a"),
      owners = mapOf("a" to "app", "x" to "lib"),
    )
    assertThat(rows(source)).containsExactly(Existing("a"), ToggleOtherProjects)
    assertThat(rows(source, showOtherProjects = true))
      .containsExactly(GroupHeader("app"), Existing("a"), GroupHeader("lib"), Existing("x"), ToggleOtherProjects)
  }

  @Test
  fun `a module without own interpreters lists none of them until the toggle`() {
    val source = InterpreterComboSource(moduleInterpreter = null, ownInterpreters = emptyList(), projectInterpreters = listOf("x", "y"))
    assertThat(rows(source)).containsExactly(ToggleOtherProjects)
    assertThat(rows(source, showOtherProjects = true)).containsExactly(Existing("x"), Existing("y"), ToggleOtherProjects)
  }

  @Test
  fun `the all interpreters row shows only when the caller offers it`() {
    val source = InterpreterComboSource(moduleInterpreter = null, ownInterpreters = listOf("a"), projectInterpreters = listOf("a"))
    assertThat(rows(source, offerShowAll = true)).containsExactly(Existing("a"), ShowAll)
  }

  @Test
  fun `the module interpreter row comes first when the caller offers it`() {
    val source = InterpreterComboSource(moduleInterpreter = "a", ownInterpreters = listOf("a", "b"), projectInterpreters = listOf("a", "b"))
    assertThat(rows(source, offerModuleInterpreter = true)).containsExactly(ModuleInterpreter, Existing("a"), Existing("b"))
  }

  @Test
  fun `the module interpreter row is the only row without interpreters`() {
    val empty = InterpreterComboSource<String>(moduleInterpreter = null, ownInterpreters = emptyList(), projectInterpreters = emptyList())
    assertThat(rows(empty, offerModuleInterpreter = true)).containsExactly(ModuleInterpreter)
  }

  @Test
  fun `a selection of another project opens the interpreters of the other projects`() {
    val source = InterpreterComboSource(moduleInterpreter = null, ownInterpreters = listOf("a"), projectInterpreters = listOf("a", "x"))
    assertThat(opensOtherProjects(source, selected = "x")).isTrue()
  }

  @Test
  fun `an own selection or no selection keeps the own interpreters`() {
    val source = InterpreterComboSource(moduleInterpreter = null, ownInterpreters = listOf("a"), projectInterpreters = listOf("a", "x"))
    assertThat(opensOtherProjects(source, selected = "a")).isFalse()
    assertThat(opensOtherProjects(source, selected = null)).isFalse()
  }

  @Test
  fun `a selection of another project opens the other projects also for a module without own interpreters`() {
    val source = InterpreterComboSource(moduleInterpreter = null, ownInterpreters = emptyList(), projectInterpreters = listOf("x"))
    assertThat(opensOtherProjects(source, selected = "x")).isTrue()
  }

  @Test
  fun `another workspace without interpreters still gives the toggle`() {
    val source = InterpreterComboSource(moduleInterpreter = "a", ownInterpreters = listOf("a"), projectInterpreters = listOf("a"), hasOtherProjects = true)
    assertThat(rows(source)).containsExactly(Existing("a"), ToggleOtherProjects)
  }

  @Test
  fun `a project of one workspace gives no toggle`() {
    val source = InterpreterComboSource(moduleInterpreter = "a", ownInterpreters = listOf("a"), projectInterpreters = listOf("a"))
    assertThat(rows(source)).containsExactly(Existing("a"))
  }

  @Test
  fun `the mode with the other projects always has the captions, also for one owner`() {
    val source = InterpreterComboSource(
      moduleInterpreter = "a",
      ownInterpreters = listOf("a"),
      projectInterpreters = listOf("a"),
      owners = mapOf("a" to "app"),
      hasOtherProjects = true,
    )
    assertThat(rows(source)).containsExactly(Existing("a"), ToggleOtherProjects)
    assertThat(rows(source, showOtherProjects = true)).containsExactly(GroupHeader("app"), Existing("a"), ToggleOtherProjects)
  }

  private fun rows(
    source: InterpreterComboSource<String>,
    showOtherProjects: Boolean = false,
    kept: List<String> = emptyList(),
    offerShowAll: Boolean = false,
    offerModuleInterpreter: Boolean = false,
  ): List<SdkComboItem<String>> = interpreterComboRows(source, showOtherProjects, kept, offerShowAll, offerModuleInterpreter)
}
