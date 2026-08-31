// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.python.sdk.common.evolution.EvoNodeDto
import com.intellij.python.sdk.common.evolution.EvoNodeKind
import com.intellij.python.sdk.common.evolution.PyInterpreterDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
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
import org.junit.jupiter.api.Test

/**
 * Guards the tool list the interpreter widget draws, as the popup gets it — the rows, and the row that folds them.
 *
 * Written against the built tree rather than against the rule behind it, because the two disagreed once: the rule said
 * "show every tool", the popup drew every tool *and* a "Show Less" row under them, and a test of the rule alone was
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
      toolsExpanded = toolsExpanded,
      setToolsExpanded = {},
    ).buildTree(DataContext.EMPTY_CONTEXT)

  /** Every row of the tree, in the order the popup lists them. */
  private fun rows(tree: EvoTreeStaticNodeElement): List<String> =
    tree.sections.flatMap { it.elements }.map { it.presentation.text.orEmpty() }

  /** The text of the fold/unfold row, or null when the popup draws none — which is what most of this asserts. */
  private fun toggle(tree: EvoTreeStaticNodeElement): String? =
    tree.sections.flatMap { it.elements }
      .filterIsInstance<EvoTreeLeafElement>()
      .firstOrNull { it.action is EvoDisclosureRow }
      ?.presentation?.text

  @Test
  fun `no interpreter shows every tool and no toggle`() {
    // PY-91389: the widget drew all four tools and a "Show Less" under them, offering to fold a list that was never
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
  fun `the tool in use leads and the rest fold behind a Show More row`() {
    val tree = tree(interpreter = interpreter(activeNodeId = "poetry"), nodes = tools(4))
    val shown = rows(tree).filter { it in setOf("uv", "poetry", "conda", "pip") }
    assertEquals(listOf("poetry"), shown)
    assertTrue(toggle(tree)?.contains("More") == true, "expected a Show More row, got ${toggle(tree)}")
  }

  @Test
  fun `an interpreter no tool owns folds them all away`() {
    // A remote interpreter, or one of a flavor no node claims: none of the tools is worth singling out.
    val tree = tree(interpreter = interpreter(activeNodeId = null), nodes = tools(4))
    assertEquals(emptyList<String>(), rows(tree).filter { it in setOf("uv", "poetry", "conda", "pip") })
    assertTrue(toggle(tree) != null, "expected a row offering the hidden tools")
  }

  @Test
  fun `an unfolded list shows every tool and folds back`() {
    val tree = tree(interpreter = interpreter(activeNodeId = "poetry"), nodes = tools(4), toolsExpanded = true)
    assertEquals(listOf("uv", "poetry", "conda", "pip"), rows(tree).filter { it in setOf("uv", "poetry", "conda", "pip") })
    assertTrue(toggle(tree)?.contains("Less") == true, "expected a Show Less row, got ${toggle(tree)}")
  }

  @Test
  fun `the only tool there is needs no toggle`() {
    val tree = tree(interpreter = interpreter(activeNodeId = "uv"), nodes = tools(1))
    assertEquals(listOf("uv"), rows(tree).filter { it == "uv" })
    assertEquals(null, toggle(tree))
  }
}
