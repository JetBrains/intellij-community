// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.hyperlinks

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.hyperlinks.TerminalFilterResultInfo
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHighlightingInfo
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinksModel
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalHyperlinksModelTest {
  private val model = TerminalHyperlinksModel(debugName = "Test")

  /** Links with end offsets 10, 20, 30, 40, 50 (ids 1..5), each spanning `[end - 5, end]`. */
  private fun addFiveLinks() {
    model.addHyperlinks((1..5).map { link(id = it.toLong(), end = it * 10L) })
  }

  // --- getHyperlink -------------------------------------------------------------------------------------------------

  @Test
  fun `getHyperlink returns null for an unknown id`() {
    // Empty model.
    assertThat(model.getHyperlink(TerminalHyperlinkId(99L))).isNull()
    // Populated model, id that was never added.
    addFiveLinks()
    assertThat(model.getHyperlink(TerminalHyperlinkId(99L))).isNull()
  }

  @Test
  fun `getHyperlink returns the stored link with its offsets`() {
    addFiveLinks()
    val link3 = model.getHyperlink(TerminalHyperlinkId(3L))
    assertThat(link3).isNotNull
    assertThat(link3!!.absoluteStartOffset).isEqualTo(25L)
    assertThat(link3.absoluteEndOffset).isEqualTo(30L)
  }

  // --- addHyperlinks ------------------------------------------------------------------------------------------------

  @Test
  fun `all added links are retrievable`() {
    addFiveLinks()
    assertPresent(1L, 2L, 3L, 4L, 5L)
  }

  @Test
  fun `adding an empty list keeps existing links`() {
    addFiveLinks()
    model.addHyperlinks(emptyList())
    assertPresent(1L, 2L, 3L, 4L, 5L)
  }

  @Test
  fun `input order does not matter, links are kept sorted by end offset`() {
    // Add out of order; the internal list must still end up sorted by end offset,
    // otherwise the binary-search-based range removal below would remove the wrong links.
    model.addHyperlinks(listOf(
      link(id = 5, end = 50),
      link(id = 1, end = 10),
      link(id = 3, end = 30),
      link(id = 2, end = 20),
      link(id = 4, end = 40),
    ))
    val removed = model.removeHyperlinks(
      fromAbsoluteOffset = 25,
      toAbsoluteOffset = 45
    ).ids()
    // End offsets within [25, 45] are 30 and 40.
    assertThat(removed).containsExactlyInAnyOrder(3L, 4L)
    assertPresent(1L, 2L, 5L)
    assertAbsent(3L, 4L)
  }

  @Test
  fun `a later batch interleaves with existing links keeping the merged order`() {
    model.addHyperlinks(listOf(link(id = 1, end = 10), link(id = 3, end = 30), link(id = 5, end = 50)))
    // These fall between the already-present links; the merge must interleave them, not append.
    model.addHyperlinks(listOf(link(id = 2, end = 20), link(id = 4, end = 40)))
    val removed = model.removeHyperlinks(fromAbsoluteOffset = 25, toAbsoluteOffset = 45).ids()
    assertThat(removed).containsExactlyInAnyOrder(3L, 4L)
    assertPresent(1L, 2L, 5L)
    assertAbsent(3L, 4L)
  }

  @Test
  fun `links with equal end offsets are both kept and both removable`() {
    model.addHyperlinks(listOf(link(id = 1, end = 10), link(id = 5, end = 50)))
    model.addHyperlinks(listOf(link(id = 3, start = 25, end = 30), link(id = 6, start = 28, end = 30)))
    assertPresent(3L, 6L)
    // Both links end exactly at 30, so an exact-bound removal takes both.
    val removed = model.removeHyperlinks(fromAbsoluteOffset = 30, toAbsoluteOffset = 30).ids()
    assertThat(removed).containsExactlyInAnyOrder(3L, 6L)
    assertPresent(1L, 5L)
    assertAbsent(3L, 6L)
  }

  // --- removeHyperlinks: offset range -------------------------------------------------------------------------------

  @Test
  fun `removing from an empty model returns nothing`() {
    val removed = model.removeHyperlinks(fromAbsoluteOffset = 0).ids()
    assertThat(removed).isEmpty()
  }

  @Test
  fun `unbounded removal removes everything from the offset`() {
    addFiveLinks()
    val removed = model.removeHyperlinks(fromAbsoluteOffset = 25).ids()
    // End offsets >= 25 are 30, 40, 50.
    assertThat(removed).containsExactlyInAnyOrder(3L, 4L, 5L)
    assertPresent(1L, 2L)
    assertAbsent(3L, 4L, 5L)
  }

  @Test
  fun `unbounded removal from offset zero removes all links`() {
    addFiveLinks()
    val removed = model.removeHyperlinks(fromAbsoluteOffset = 0).ids()
    assertThat(removed).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L)
    assertAbsent(1L, 2L, 3L, 4L, 5L)
  }

  @Test
  fun `removal above all links removes nothing`() {
    addFiveLinks()
    // All end offsets are <= 50, so nothing reaches offset 100.
    val removed = model.removeHyperlinks(fromAbsoluteOffset = 100).ids()
    assertThat(removed).isEmpty()
    assertPresent(1L, 2L, 3L, 4L, 5L)
  }

  @Test
  fun `bounded removal keeps links ending past the upper bound`() {
    addFiveLinks()
    val removed = model.removeHyperlinks(fromAbsoluteOffset = 25, toAbsoluteOffset = 45).ids()
    // End offsets within [25, 45] are 30 and 40. 50 ends past the bound and is kept.
    assertThat(removed).containsExactlyInAnyOrder(3L, 4L)
    assertPresent(1L, 2L, 5L)
    assertAbsent(3L, 4L)
  }

  @Test
  fun `bounds are inclusive on both ends`() {
    addFiveLinks()
    // A single link ends exactly at both bounds.
    val removed = model.removeHyperlinks(fromAbsoluteOffset = 30, toAbsoluteOffset = 30).ids()
    assertThat(removed).containsExactly(3L)
    assertPresent(1L, 2L, 4L, 5L)
    assertAbsent(3L)
  }

  @Test
  fun `empty range removes nothing from the offset part`() {
    addFiveLinks()
    // from > to: no link's end offset can fall into the range.
    val removed = model.removeHyperlinks(fromAbsoluteOffset = 45, toAbsoluteOffset = 25).ids()
    assertThat(removed).isEmpty()
    assertPresent(1L, 2L, 3L, 4L, 5L)
  }

  @Test
  fun `a link is removed by its end offset even when it starts before the lower bound`() {
    model.addHyperlinks(listOf(
      link(id = 1, start = 0, end = 10),   // fully before the range
      link(id = 2, start = 20, end = 35),  // starts before from=30 but ends inside [30, 40]
      link(id = 3, start = 60, end = 70),  // fully after the range
    ))
    val removed = model.removeHyperlinks(fromAbsoluteOffset = 30, toAbsoluteOffset = 40).ids()
    // Removal is keyed on the end offset: link 2 ends at 35 (within the range), so it is removed
    // even though it starts at 20, before the lower bound.
    assertThat(removed).containsExactly(2L)
    assertPresent(1L, 3L)
    assertAbsent(2L)
  }

  @Test
  fun `a link that ends past the upper bound is kept even if it overlaps the range`() {
    model.addHyperlinks(listOf(
      link(id = 1, start = 20, end = 55),  // spans the whole [30, 40] range but ends at 55
      link(id = 2, start = 32, end = 38),  // fully inside the range
    ))
    val removed = model.removeHyperlinks(fromAbsoluteOffset = 30, toAbsoluteOffset = 40).ids()
    // Again keyed on the end offset: link 1 ends at 55 (past to=40), so it is kept despite overlapping.
    assertThat(removed).containsExactly(2L)
    assertPresent(1L)
    assertAbsent(2L)
  }

  // --- removeHyperlinks: trimming -----------------------------------------------------------------------------------

  @Test
  fun `trimmed links are removed regardless of the bounds`() {
    addFiveLinks()
    // The bounded range catches no link, but the trimmed head (end offset <= 15) must still be removed.
    val removed = model.removeHyperlinks(
      fromAbsoluteOffset = 100,
      toAbsoluteOffset = 100,
      trimUntilOffset = 15L,
    ).ids()
    assertThat(removed).containsExactly(1L)
    assertPresent(2L, 3L, 4L, 5L)
    assertAbsent(1L)
  }

  @Test
  fun `trimming removes every link ending within the trimmed region`() {
    addFiveLinks()
    val removed = model.removeHyperlinks(
      fromAbsoluteOffset = 100,
      toAbsoluteOffset = 100,
      trimUntilOffset = 35L,
    ).ids()
    // End offsets <= 35 are 10, 20, 30.
    assertThat(removed).containsExactlyInAnyOrder(1L, 2L, 3L)
    assertPresent(4L, 5L)
    assertAbsent(1L, 2L, 3L)
  }

  @Test
  fun `a link is trimmed only when its end offset is within the trimmed region`() {
    model.addHyperlinks(listOf(
      link(id = 1, start = 0, end = 10),   // ends before the trim offset
      link(id = 2, start = 10, end = 25),  // straddles the trim offset: starts at 10, ends at 25
    ))
    val removed = model.removeHyperlinks(
      fromAbsoluteOffset = 100,
      toAbsoluteOffset = 100,
      trimUntilOffset = 15L,
    ).ids()
    // Only link 1 (end 10 <= 15) is trimmed; link 2 is kept because its end offset (25) is past the
    // trim offset, even though it starts inside the trimmed region.
    assertThat(removed).containsExactly(1L)
    assertPresent(2L)
    assertAbsent(1L)
  }

  @Test
  fun `a link ending exactly at the trim offset is trimmed`() {
    model.addHyperlinks(listOf(link(id = 1, end = 15), link(id = 2, end = 25)))
    val removed = model.removeHyperlinks(
      fromAbsoluteOffset = 100,
      toAbsoluteOffset = 100,
      trimUntilOffset = 15L,
    ).ids()
    // Trimming is inclusive of the trim offset itself.
    assertThat(removed).containsExactly(1L)
    assertPresent(2L)
    assertAbsent(1L)
  }

  @Test
  fun `trimming and range removal apply together in a single call`() {
    addFiveLinks()
    val removed = model.removeHyperlinks(
      fromAbsoluteOffset = 35,
      toAbsoluteOffset = 45,
      trimUntilOffset = 15L, // trims id 1 (end 10)
    ).ids()
    // Trim removes id 1 (end 10 <= 15); the range [35, 45] removes id 4 (end 40).
    assertThat(removed).containsExactlyInAnyOrder(1L, 4L)
    assertPresent(2L, 3L, 5L)
    assertAbsent(1L, 4L)
  }

  private fun link(id: Long, end: Long): TerminalFilterResultInfo = link(id = id, start = end - 5, end = end)

  private fun link(id: Long, start: Long, end: Long): TerminalFilterResultInfo =
    TerminalHighlightingInfo(
      id = TerminalHyperlinkId(id),
      absoluteStartOffset = start,
      absoluteEndOffset = end,
      style = null,
      layer = 0,
    )

  private fun Collection<TerminalHyperlinkId>.ids(): List<Long> = map { it.value }

  private fun assertPresent(vararg ids: Long) {
    for (id in ids) {
      assertThat(model.getHyperlink(TerminalHyperlinkId(id))).`as`("link $id should be present").isNotNull()
    }
  }

  private fun assertAbsent(vararg ids: Long) {
    for (id in ids) {
      assertThat(model.getHyperlink(TerminalHyperlinkId(id))).`as`("link $id should be absent").isNull()
    }
  }
}
