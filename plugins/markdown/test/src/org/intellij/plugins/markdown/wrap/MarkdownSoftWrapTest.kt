// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.wrap

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.SlowOperations
import org.intellij.plugins.markdown.editor.tables.ui.MarkdownInlayUpdateOnSoftWrapListener
import org.junit.jupiter.api.assertDoesNotThrow

class MarkdownSoftWrapTest : BasePlatformTestCase() {
  /**
   * The long token hides the previous space from the ten-character fast path.
   * This forces the Markdown strategy to reject dots inside the address.
   */
  fun `test soft wrap does not break an IPv4 address`() {
    val address = "192.168.0.1"
    val text = "word ".repeat(10) + "unbreakable".repeat(2) + "$address more words after the address"
    myFixture.configureByText("softWrapIpv4.md", text)
    assertTrue(EditorTestUtil.configureSoftWraps(myFixture.editor, 80))

    val addressStart = text.indexOf(address)
    val wrapOffsets = myFixture.editor.softWrapModel.getSoftWrapsForRange(0, text.length).map { it.start }
    assertNotEmpty(wrapOffsets)
    assertEmpty("soft wrap must not appear inside the IPv4 address: $wrapOffsets",
                wrapOffsets.filter { it in (addressStart + 1) until (addressStart + address.length) })
  }

  fun `test listener does not throw or report slow operations on EDT`() {
    myFixture.configureByText("test.md", "")
    val listener = MarkdownInlayUpdateOnSoftWrapListener()

    SlowOperations.reportKnownIssues()
    PlatformTestUtil.withSystemProperty<Nothing>(SlowOperations.FORBID_SLOW_OPS_PROPERTY, "true") {
      assertDoesNotThrow {
        SlowOperations.startSection(SlowOperations.FORCE_ASSERT).use {
          listener.editorCreated(EditorFactoryEvent(EditorFactory.getInstance(), myFixture.editor))
        }
      }
    }
    assertTrue(SlowOperations.reportKnownIssues().isEmpty())
  }

  /**
   * The wrap candidate falls inside the link, where wrapping is forbidden, so the strategy has to move
   * the soft wrap before the link start. The link is placed after several blank-line-separated paragraphs to check
   * that [org.intellij.plugins.markdown.editor.MarkdownLineWrapPositionStrategy] resolves offsets correctly when
   * the line under test does not start at the beginning of the document.
   */
  fun `test soft wrap does not break a link located after the first block`() {
    val filler = "Introductory paragraph that occupies space before the block under test.\n\n".repeat(3)
    val prefix = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu"
    val line = "$prefix [anchor](https://example.com/long/path/kept/intact) when the paragraph continues with several ordinary words"
    val text = filler + line + "\n"
    myFixture.configureByText("softWrapLink.md", text)
    assertTrue(EditorTestUtil.configureSoftWraps(myFixture.editor, 80))

    val lineStart = filler.length
    val linkStart = text.indexOf("[anchor]")
    val linkEnd = text.indexOf(')', linkStart) + 1
    val wrapOffsets = myFixture.editor.softWrapModel.getSoftWrapsForRange(0, text.length).map { it.start }
    assertNotEmpty(wrapOffsets)
    assertEmpty("soft wraps must not appear inside the inline link at [$linkStart, $linkEnd): $wrapOffsets",
                wrapOffsets.filter { it in (linkStart + 1) until linkEnd })
    assertTrue("expected the first visual row to wrap at or before the link start $linkStart: $wrapOffsets",
               wrapOffsets.any { it in (lineStart + 1)..linkStart })
  }

  fun `test soft wrap goes in front of a bare URL`() {
    assertSoftWraps(
      "- `[#q-1]` — Since we changed direction to ⏎https://git.example.com/group/project/-/merge_requests/1234",
      "- [ ] [#bl-1] see the notes in the tracker before you start, then ⏎https://git.example.com/group/project/-/issues/42",
      "- See [the notes](#notes) before you start, then ⏎https://git.example.com/group/project/-/issues/42",
      "Since we changed direction to the plan, see ⏎https://git.example.com/group/project/-/merge_requests/1234",
      // The second wrap is on a continuation line, after the `[` of the line.
      "- [#q-1] the plan changed after the review, so we start again with an ⏎internationalization see the notes in " +
      "⏎https://git.example.com/group/project/-/merge_requests/1234",
    )
  }

  fun `test soft wrap goes in front of a link token`() {
    assertSoftWraps(
      "- See [the notes in the tracker]( ⏎https://git.example.com/group/project/-/issues/42 )",
      "- [#id] Run the smoke test of the build as described in the runbook ⏎(procedure-for-the-nightly-build), install (a)",
      "See the notes in the tracker before you start working on it, then ⏎[the-notes-about-the-migration](#notes)",
      "- `[#q-1]` — Since we changed direction to ⏎<https://git.example.com/group/project/-/merge_requests/1234>",
      "- [#q-1] For the access to the tracker, write a message to ⏎<first.last.with-a-long-name@git.example.com>",
    )
  }

  fun `test soft wrap goes inside link text, titles and definitions`() {
    assertSoftWraps(
      "See the notes in the tracker before you start, [then read ⏎the-notes-about-the-migration](#notes)",
      "See [the notes](#notes \"the notes about ⏎the-migration-of-the-tracker-to-the-new-host\") before you start",
      "[the notes about the migration of the tracker]: ⏎https://git.example.com/group/project/-/merge_requests/1234",
      "[notes]: #notes \"the notes about the migration of the tracker to ⏎the-new-host-of-the-group-and-project\"",
    )
  }

  /**
   * Puts each line in its own block and checks its soft wraps at 80 columns.
   * The mock font gives each character the width that [EditorTestUtil.configureSoftWraps] expects.
   */
  private fun assertSoftWraps(vararg markedLines: String) {
    try {
      FontLayoutService.setInstance(MockFontLayoutService(10, 10, 2))
      myFixture.configureByText("softWrap.md", markedLines.joinToString("\n\n") { line -> line.filterNot { it == WRAP } })
      assertTrue(EditorTestUtil.configureSoftWraps(myFixture.editor, 80))

      val document = myFixture.editor.document
      val actual = markedLines.indices.map { index ->
        val line = index * 2
        val lineStart = document.getLineStartOffset(line)
        val wraps = myFixture.editor.softWrapModel.getSoftWrapsForLine(line).map { it.start - lineStart }
        buildString {
          document.charsSequence.subSequence(lineStart, document.getLineEndOffset(line)).forEachIndexed { offset, char ->
            if (offset in wraps) append(WRAP)
            append(char)
          }
        }
      }
      assertEquals(markedLines.joinToString("\n"), actual.joinToString("\n"))
    }
    finally {
      FontLayoutService.setInstance(null)
    }
  }
}

// Symbol that indicates the wrap position.
private const val WRAP = '⏎'
