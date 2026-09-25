// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoLeafKind
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoNodeKind
import com.intellij.python.sdk.common.evolution.PyInterpreterDto
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.python.sdk.frontend.evolution.EvoPySdkSwitchPopupFactory
import com.intellij.python.sdk.frontend.evolution.components.EvoDisclosureRow
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeLeafElement
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeStaticNodeElement
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Guards the tool list the interpreter widget draws, as the popup gets it — the rows, and the row that folds them.
 *
 * Written against the built tree rather than against the rule behind it, because the two disagreed once: the rule said
 * "show every tool", the popup drew every tool *and* a fold-back row under them, and a test of the rule alone was
 * green while the widget was wrong. The disclosure row is what this asserts on, so it cannot be green again while a
 * toggle is on screen.
 */
@TestApplication
class PyEvoWidgetToolListTest {
  private val projectFixture = projectFixture()

  /** Four tools, the shape of a real widget: uv, Poetry, Conda, pip. */
  private fun tools(count: Int): List<EvoNodeDto> =
    listOf("uv", "poetry", "conda", "pip").take(count).map {
      EvoNodeDto(id = it, label = it, icon = AllIcons.Language.Python.rpcId(), kind = EvoNodeKind.TOOL, fusId = it)
    }

  private fun interpreter(activeNodeId: String?): PyInterpreterDto =
    PyInterpreterDto(
      title = "myenv [3.13.1]",
      description = "/home/me/project/.venv/bin/python",
      icon = AllIcons.Language.Python.rpcId(),
      ref = PyInterpreterRef.DetectedPath("/home/me/project/.venv/bin/python"),
      activeNodeId = activeNodeId,
    )

  private fun tree(interpreter: PyInterpreterDto?, nodes: List<EvoNodeDto>, toolsExpanded: Boolean = false): EvoTreeStaticNodeElement =
    build(interpreter, nodes).also { it.isFolded = !toolsExpanded }

  /** The tree as the factory builds it, holding every tool — folded, which is how a popup opens. */
  private fun build(
    interpreter: PyInterpreterDto?,
    nodes: List<EvoNodeDto>,
    packageManagerActions: List<EvoLeafDto> = emptyList(),
  ): EvoTreeStaticNodeElement =
    EvoPySdkSwitchPopupFactory(
      project = projectFixture.get(),
      pyProjectKey = "key",
      displayName = "project",
      workspaceRootName = null,
      currentInterpreter = interpreter,
      nodes = nodes,
      associated = emptyList(),
      shortcuts = emptyList(),
      // Never used by this: only a lazy tool node needs it, and nothing here opens one.
      scope = @OptIn(DelicateCoroutinesApi::class) GlobalScope,
      expandTools = {},
      packageManagerActions = packageManagerActions,
    ).buildTree()

  /** A package-manager row as the backend sends one — an ACTION leaf run back by its own action id. */
  private fun packageManagerAction(title: String): EvoLeafDto =
    EvoLeafDto(title = title, icon = AllIcons.Actions.Install.rpcId(), kind = EvoLeafKind.ACTION, actionId = title)

  /**
   * Every row the popup lists, in order.
   *
   * Filtered by the fold, the way `EvoActionPopupStep.getValues` filters it: the tree holds every tool at all times, and
   * the fold is what decides which of them a popup shows.
   */
  private fun rows(tree: EvoTreeStaticNodeElement): List<String> =
    tree.sections.flatMap { it.elements }.filter { tree.shows(it) }.map { it.presentation.text.orEmpty() }

  /** The text of the unfold row, or null when the popup draws none — which is what most of this asserts. */
  private fun toggle(tree: EvoTreeStaticNodeElement): String? =
    tree.sections.flatMap { it.elements }
      .filter { tree.shows(it) }
      .filterIsInstance<EvoTreeLeafElement>()
      .firstOrNull { it.action is EvoDisclosureRow }
      ?.presentation?.text

  @Test
  fun `no interpreter shows every tool and no toggle`() {
    // PY-91389: the widget drew all four tools and a fold-back row under them, offering to fold a list that was never
    // folded. Nothing is configured yet, so picking a tool is the whole point of this list.
    val tree = tree(interpreter = null, nodes = tools(4))
    assertEquals(listOf("uv", "poetry", "conda", "pip"), rows(tree).filter { it in setOf("uv", "poetry", "conda", "pip") })
    assertEquals(null, toggle(tree))
  }

  @Test
  fun `no interpreter keeps every tool even after the list was unfolded elsewhere`() {
    // The expand state belongs to the widget and outlives the project's interpreter, so it can arrive set here.
    val tree = tree(interpreter = null, nodes = tools(4), toolsExpanded = true)
    assertEquals(null, toggle(tree))
  }

  @Test
  fun `the tool in use leads and the rest fold behind a Show more row`() {
    val tree = tree(interpreter = interpreter(activeNodeId = "poetry"), nodes = tools(4))
    val shown = rows(tree).filter { it in setOf("uv", "poetry", "conda", "pip") }
    assertEquals(listOf("poetry"), shown)
    assertTrue(toggle(tree)?.contains("more", ignoreCase = true) == true, "expected a Show more row, got ${toggle(tree)}")
  }

  @Test
  fun `an interpreter no tool owns folds them all away`() {
    // A remote interpreter, or one of a flavor no node claims: none of the tools is worth singling out.
    val tree = tree(interpreter = interpreter(activeNodeId = null), nodes = tools(4))
    assertEquals(emptyList<String>(), rows(tree).filter { it in setOf("uv", "poetry", "conda", "pip") })
    assertTrue(toggle(tree) != null, "expected a row offering the hidden tools")
  }

  @Test
  fun `an unfolded list shows every tool and offers no row under them`() {
    // Unfolding is one-way: the list the user asked for stands, and the next popup opens folded again by itself.
    val tree = tree(interpreter = interpreter(activeNodeId = "poetry"), nodes = tools(4), toolsExpanded = true)
    assertEquals(listOf("uv", "poetry", "conda", "pip"), rows(tree).filter { it in setOf("uv", "poetry", "conda", "pip") })
    assertEquals(null, toggle(tree))
  }

  @Test
  @DisplayName("unfolding shows the rest of the tree it was already holding, rather than a tree of its own")
  fun `one tree serves both the folded and the unfolded list`() {
    // A rebuild threw away whatever the tool nodes had loaded, so a submenu the user had opened was scanned again.
    val tree = build(interpreter = interpreter(activeNodeId = "poetry"), nodes = tools(4))
    val held = tree.sections.flatMap { it.elements }

    assertEquals(listOf("poetry"), rows(tree).filter { it in setOf("uv", "poetry", "conda", "pip") })

    tree.isFolded = false

    assertEquals(listOf("uv", "poetry", "conda", "pip"), rows(tree).filter { it in setOf("uv", "poetry", "conda", "pip") })
    assertEquals(held, tree.sections.flatMap { it.elements }, "unfolding must not replace a single row")
  }

  @Test
  fun `the only tool there is needs no toggle`() {
    val tree = tree(interpreter = interpreter(activeNodeId = "uv"), nodes = tools(1))
    assertEquals(listOf("uv"), rows(tree).filter { it == "uv" })
    assertEquals(null, toggle(tree))
  }

  @Test
  @DisplayName("the package-manager rows are the backend's answer, drawn as it comes")
  fun `the current environment section draws the package-manager rows the backend sent`() {
    // PY-92487: which of that group's actions apply is decided on the backend, where the interpreter's package manager
    // and its dependency file are. The frontend used to ask its own ActionManager and run each action's update() to
    // find out — which in RemDev answers with a delegating wrapper that decides nothing, leaving every tool's rows on
    // screen at once. So this asserts on what the popup draws for a given backend answer, and on nothing else being
    // there: a frontend that went back to deciding for itself would show rows this never sent it.
    val sent = listOf(packageManagerAction("uv Lock"), packageManagerAction("uv Sync"))
    val tree = build(interpreter = interpreter(activeNodeId = "uv"), nodes = tools(1), packageManagerActions = sent)

    val drawn = currentEnvironmentRows(tree)
    assertEquals(listOf("uv Lock", "uv Sync"), drawn.filter { it in setOf("uv Lock", "uv Sync", "Poetry Lock", "Conda Export") },
                 "expected exactly the rows the backend sent, in order")

    // The rows around them are the section's own and stay put, so the assertion above is about the group and not about
    // an empty section.
    assertTrue(drawn.size > sent.size, "expected the section's own rows beside them, got $drawn")
  }

  @Test
  fun `no package-manager row is drawn when the backend sent none`() {
    val tree = build(interpreter = interpreter(activeNodeId = "uv"), nodes = tools(1))
    val drawn = currentEnvironmentRows(tree)
    assertTrue(drawn.none { it in setOf("uv Lock", "uv Sync", "Poetry Lock", "Conda Export") },
               "expected no package-manager row, got $drawn")
  }

  /**
   * The rows of the "Current Environment" section — the last one, which is where the package-manager rows sit.
   *
   * Found by position rather than by its caption, so this does not turn on the wording: the section that acts on
   * whatever interpreter is current is always last (see `EvoPySdkSwitchPopupFactory.sectionsWith`).
   */
  private fun currentEnvironmentRows(tree: EvoTreeStaticNodeElement): List<String> =
    tree.sections.last().elements.map { it.presentation.text.orEmpty() }
}
