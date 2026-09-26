// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.hyperlinks

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.InvisibleHyperlinkFilterProvider
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.terminal.frontend.view.hyperlinks.HOVER_CACHED_LINES
import com.intellij.testFramework.LoggedErrorProcessor
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.hyperlinks.filter.TerminalFilterScope
import org.jetbrains.plugins.terminal.hyperlinks.filter.TerminalGenericFileFilter
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests of the invisible hyperlinks computed for the line under the mouse pointer,
 * see `FrontendInvisibleHyperlinksProcessing`.
 */
@RunWith(JUnit4::class)
internal class TerminalInvisibleHyperlinksProcessingTest : TerminalHyperlinksProcessingTestBase() {

  @Test
  fun `invisible links are computed for hovered lines only`() = withFixture {
    updateModel(0L, """
      0: line0 hover0
      1: line1 hover1
    """.trimIndent())
    assertText("""
      0: line0 hover0
      1: line1 hover1
    """.trimIndent())
    assertHoverLinks()
    hover(at(0, "hover0"))
    assertHoverLinks(at(0, "hover0"))
    hover(at(1, "hover1"))
    assertHoverLinks(at(0, "hover0"), at(1, "hover1"))
  }

  @Test
  fun `recently hovered lines are not requested again`() = withFixture {
    updateModel(0L, """
      0: line0 hover0
      1: line1 hover1
    """.trimIndent())
    hover(at(0, "hover0"))
    hover(at(1, "hover1"))
    hover(at(0, "hover0"))
    assertHoverLinks(at(0, "hover0"), at(1, "hover1"))
    assertThat(hoverFilter.appliedTexts).hasSize(2)
  }

  @Test
  fun `link of a line hovered long ago can still be followed`() = withFixture {
    val lines = 0 until HOVER_CACHED_LINES
    updateModel(0L, lines.joinToString("\n") { "$it: hover$it" })
    for (line in lines) {
      hover(at(line, "hover$line"))
    }
    ctrlClick(at(0, "hover0"))
    assertClickedLinks("hover0")
  }

  @Test
  fun `earliest hovered line is dropped beyond the cache size`() = withFixture {
    val lines = 0..HOVER_CACHED_LINES
    updateModel(0L, lines.joinToString("\n") { "$it: hover$it" })
    for (line in lines) {
      hover(at(line, "hover$line"))
    }
    assertHoverLinks(*(1..HOVER_CACHED_LINES).map { at(it, "hover$it") }.toTypedArray())
    hover(at(0, "hover0"))
    assertThat(hoverFilter.appliedTexts).hasSize(HOVER_CACHED_LINES + 2)
  }

  @Test
  fun `invisible link is followed on ctrl-click`() = withFixture {
    updateModel(0L, "0: line0 hover0")
    hover(at(0, "hover0"))
    ctrlClick(at(0, "hover0"))
    assertClickedLinks("hover0")
  }

  @Test
  fun `hovered invisible link gets the hover effect without a mouse move`() = withFixture {
    updateModel(0L, "0: line0 hover0")
    hover(at(0, "hover0"))
    assertThat(hoveredHyperlink()).isNotNull()
  }

  @Test
  fun `link added under a resting pointer gets the hover effect`() = withFixture {
    updateModel(0L, "0: line0 text0")
    assertText("0: line0 text0")
    moveMouse(at(0, "text0"))
    updateModel(0L, "0: line0 link0")
    assertLinks(link(at(0, "link0")))
    assertThat(hoveredHyperlink()).isNotNull()
  }

  @Test
  fun `cached hover links are dropped on any content change`() = withFixture {
    updateModel(0L, """
      0: line0 hover0
      1: line1 hover1
      2: line2
    """.trimIndent())
    hover(at(0, "hover0"))
    hover(at(1, "hover1"))
    val processedBefore = processedHoversCount()
    updateModel(2L, "2: changed")
    awaitHoverProcessed(processedBefore)
    assertHoverLinks(at(1, "hover1")) // only the hovered line is requested again
    assertThat(hoverFilter.appliedTexts).hasSize(3)
  }

  @Test
  fun `invisible links are recomputed when the hovered line changes`() = withFixture {
    updateModel(0L, "0: line0 hover0")
    hover(at(0, "hover0"))
    assertHoverLinks(at(0, "hover0"))
    val processedBefore = processedHoversCount()
    updateModel(0L, "0: line0 hover2")
    awaitHoverProcessed(processedBefore)
    assertText("0: line0 hover2")
    assertHoverLinks(at(0, "hover2"))
  }

  @Test
  fun `whole hovered line is scanned regardless of its length`() = withFixture(maxOutputLength = 100_000) {
    updateModel(0L, "hover0 ${"a".repeat(20_000)} hover1")
    hover(at(0, "hover1"))
    assertHoverLinks(at(0, "hover0"), at(0, "hover1"))
    assertThat(hoverFilter.appliedTexts).hasSize(1)
  }

  @Test
  fun `every invisible link of a hovered line can be followed`() = withFixture {
    updateModel(0L, (0 until 300).joinToString(" ") { "hover$it" })
    hover(at(0, "hover0"))
    ctrlClick(at(0, "hover0"))
    ctrlClick(at(0, "hover299"))
    assertClickedLinks("hover0", "hover299")
  }

  @Test
  fun `no hover requests after the pointer leaves the editor`() = withFixture {
    updateModel(0L, "0: line0 hover0")
    hover(at(0, "hover0"))
    assertHoverLinks(at(0, "hover0"))
    exitEditor()
    val requestsBefore = hoverFilter.appliedTexts.size
    val processedBefore = processedHoversCount()
    updateModel(0L, "0: line0 hover2")
    awaitHoverProcessed(processedBefore)
    assertHoverLinks()
    assertThat(hoverFilter.appliedTexts).hasSize(requestsBefore)
  }

  @Test
  fun `hover request is repeated when the line changes during a slow request`() = withFixture {
    updateModel(0L, "0: line0 hover0")
    awaitEventProcessing()
    hoverFilter.delayPerLine = 300
    moveMouse(at(0, "hover0"))
    awaitHoverFilterCalls(1)
    updateModel(0L, "0: line0 hover2")
    awaitHoverLinks(at(0, "hover2"))
  }

  @Test
  fun `hover links are dropped when the hovered line is trimmed`() = withFixture(maxOutputLength = 100) {
    updateModel(0L, "0: line0 hover0")
    hover(at(0, "hover0"))
    assertHoverLinks(at(0, "hover0"))
    val processedBefore = processedHoversCount()
    updateModel(1L, (1..20).joinToString("\n") { "$it: line$it" })
    awaitHoverProcessed(processedBefore)
    assertHoverLinks()
  }

  @Test
  fun `generic file filter leaves the eager path when invisible links are computed on hover`() {
    Registry.get("terminal.generic.hyperlinks").setValue(true, testRootDisposable)
    fun filters(invisibleHyperlinksOnHover: Boolean): List<Filter> {
      Registry.get("terminal.hyperlinks.on.hover").setValue(invisibleHyperlinksOnHover, testRootDisposable)
      return runReadActionBlocking {
        ConsoleViewUtil.computeConsoleFilters(project, null, TerminalFilterScope(project, null))
      }
    }
    assertThat(filters(invisibleHyperlinksOnHover = false)).anyMatch { it is TerminalGenericFileFilter }
    assertThat(filters(invisibleHyperlinksOnHover = true)).noneMatch { it is TerminalGenericFileFilter }
  }

  @Test
  fun `hover file filter is provided only when invisible links are computed on hover`() {
    Registry.get("terminal.generic.hyperlinks").setValue(true, testRootDisposable)
    fun hoverFilters(invisibleHyperlinksOnHover: Boolean): List<Filter> {
      Registry.get("terminal.hyperlinks.on.hover").setValue(invisibleHyperlinksOnHover, testRootDisposable)
      return InvisibleHyperlinkFilterProvider.EP_NAME.extensionList.flatMap { it.getFilters(project, TerminalFilterScope(project, null)) }
    }
    assertThat(hoverFilters(invisibleHyperlinksOnHover = true)).anyMatch { it is TerminalGenericFileFilter }
    assertThat(hoverFilters(invisibleHyperlinksOnHover = false)).isEmpty()
  }

  @Test
  fun `a failing hover filter provider does not stop hyperlink processing`() {
    val failingProvider = object : InvisibleHyperlinkFilterProvider {
      override fun getFilters(project: Project, scope: GlobalSearchScope): List<Filter> = throw IllegalStateException("broken provider")
    }
    val loggedErrors = mutableListOf<String>()
    val errorProcessor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        loggedErrors += message
        return Action.NONE
      }
    }
    LoggedErrorProcessor.executeWith(errorProcessor).use {
      withFixture(extraHoverFilterProviders = listOf(failingProvider)) {
        updateModel(0L, "0: line0 hover0 link0")
        hover(at(0, "hover0"))
        assertHoverLinks(at(0, "hover0"))
        updateModel(0L, "0: line0 hover0 link1") // the eager path keeps working after the failure
        assertLinks(link(at(0, "link1")))
      }
    }
    assertThat(loggedErrors).isNotEmpty().allMatch { it.contains("Failed to create invisible hyperlink filters") }
  }
}
