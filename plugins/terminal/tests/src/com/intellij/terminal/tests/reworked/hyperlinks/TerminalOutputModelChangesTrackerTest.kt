// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.hyperlinks

import com.intellij.openapi.util.Disposer
import com.intellij.terminal.frontend.view.hyperlinks.TerminalOutputModelChangesTracker
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalOutputModelChangesTrackerTest : BasePlatformTestCase() {
  // --- getFirstChangedLineAndReset ---------------------------------------------------------------------------------

  @Test
  fun `initial state reports the first line, then null until the next change`() = withFixture {
    // A freshly created tracker treats the whole current content as pending.
    assertThat(tracker.getFirstChangedLineAndReset()).isEqualTo(model.firstLineIndex)
    assertThat(tracker.getFirstChangedLineAndReset()).isNull()
  }

  @Test
  fun `reports the changed line, then resets`() = withFixture {
    model.updateContent(0, "l0\nl1\nl2\nl3")
    tracker.getFirstChangedLineAndReset() // drain the initial content

    model.updateContent(0, "l0\nl1\nX2\nl3") // only line 2 changes

    assertThat(tracker.getFirstChangedLineAndReset()).isEqualTo(TerminalLineIndex.of(2))
    assertThat(tracker.getFirstChangedLineAndReset()).isNull()
  }

  @Test
  fun `reports the minimum of several lines changed before a flush`() = withFixture {
    model.updateContent(0, "l0\nl1\nl2\nl3\nl4")
    tracker.getFirstChangedLineAndReset() // drain

    model.updateContent(0, "l0\nl1\nl2\nX3\nl4") // line 3
    model.updateContent(0, "l0\nX1\nl2\nX3\nl4") // line 1 (line 3 stays changed)

    assertThat(tracker.getFirstChangedLineAndReset()).isEqualTo(TerminalLineIndex.of(1))
  }

  // --- getFirstChangedOffsetSinceStamp -----------------------------------------------------------------------------

  @Test
  fun `reports converged (end offset) when nothing changed since the stamp`() = withFixture {
    model.updateContent(0, "l0\nl1\nl2")
    val stamp = model.modificationStamp
    tracker.getFirstChangedLineAndReset()

    assertThat(tracker.getFirstChangedOffsetSinceStamp(stamp)).isEqualTo(model.endOffset)
  }

  @Test
  fun `reports the start offset of a change flushed after the stamp`() = withFixture {
    model.updateContent(0, "l0\nl1\nl2\nl3\nl4") // line starts: 0, 3, 6, 9, 12
    val snapshotStamp = model.modificationStamp
    tracker.getFirstChangedLineAndReset()

    model.updateContent(0, "l0\nl1\nX2\nl3\nl4") // change line 2
    tracker.getFirstChangedLineAndReset() // flush (record), so nothing pending is folded in

    assertThat(tracker.getFirstChangedOffsetSinceStamp(snapshotStamp)).isEqualTo(TerminalOffset.of(6))
  }

  @Test
  fun `reports the minimum offset among changes after the stamp and excludes the change at the stamp`() = withFixture {
    model.updateContent(0, "l0\nl1\nl2\nl3\nl4") // line starts: 0, 3, 6, 9, 12
    val stamp0 = model.modificationStamp
    tracker.getFirstChangedLineAndReset()

    model.updateContent(0, "l0\nl1\nX2\nl3\nl4") // change line 2 -> offset 6
    val stamp1 = model.modificationStamp
    tracker.getFirstChangedLineAndReset()

    model.updateContent(0, "l0\nl1\nX2\nl3\nX4") // change line 4 -> offset 12
    val stamp2 = model.modificationStamp
    tracker.getFirstChangedLineAndReset()

    assertThat(tracker.getFirstChangedOffsetSinceStamp(stamp0)).isEqualTo(TerminalOffset.of(6))  // min(6, 12)
    assertThat(tracker.getFirstChangedOffsetSinceStamp(stamp1)).isEqualTo(TerminalOffset.of(12)) // only the line-4 change
    assertThat(tracker.getFirstChangedOffsetSinceStamp(stamp2)).isEqualTo(model.endOffset)       // the change at the stamp is excluded
  }

  @Test
  fun `folds a change that fired before the next flush, so it is not treated as converged`() = withFixture {
    model.updateContent(0, "line0\nline1\nline2")
    val snapshotStamp = model.modificationStamp
    tracker.getFirstChangedLineAndReset()

    // Nothing changed since the snapshot => converged.
    assertThat(tracker.getFirstChangedOffsetSinceStamp(snapshotStamp)).isEqualTo(model.endOffset)

    // The document changes (line 0 rewritten) but the flush has NOT run yet.
    model.updateContent(0, "CHANGED\nline1\nline2")

    // The pending change must still be visible: not dropped, not converged, offset at the start of the change.
    val firstChanged = requireNotNull(tracker.getFirstChangedOffsetSinceStamp(snapshotStamp))
    assertThat(firstChanged).isEqualTo(model.startOffset)
    assertThat(firstChanged).isLessThan(model.endOffset)
  }

  @Test
  fun `a change at a smaller offset supersedes several later-offset changes`() = withFixture {
    model.updateContent(0, "l0\nl1\nl2\nl3\nl4") // line starts: 0, 3, 6, 9, 12
    val stamp0 = model.modificationStamp
    tracker.getFirstChangedLineAndReset()

    model.updateContent(0, "l0\nl1\nX2\nl3\nl4") // change line 2 -> offset 6
    tracker.getFirstChangedLineAndReset()
    model.updateContent(0, "l0\nl1\nX2\nl3\nX4") // change line 4 -> offset 12
    tracker.getFirstChangedLineAndReset()

    // A change that jumps back before BOTH previously recorded changes must supersede them (multiple entries dropped),
    // not just the most recent one. Otherwise, the reported minimum would be stale.
    model.updateContent(0, "X0\nl1\nX2\nl3\nX4") // change line 0 -> offset 0
    tracker.getFirstChangedLineAndReset()

    assertThat(tracker.getFirstChangedOffsetSinceStamp(stamp0)).isEqualTo(TerminalOffset.of(0))
  }

  @Test
  fun `re-querying after the flush still reports the change (folding into history is harmless)`() = withFixture {
    model.updateContent(0, "line0\nline1")
    val snapshotStamp = model.modificationStamp
    tracker.getFirstChangedLineAndReset()

    model.updateContent(0, "X\nline1")

    // The first query folds the pending change into the history on the fly.
    val beforeFlush = tracker.getFirstChangedOffsetSinceStamp(snapshotStamp)
    // The regular flush then records the same change again; the duplicate must not change the reported result.
    tracker.getFirstChangedLineAndReset()
    val afterFlush = tracker.getFirstChangedOffsetSinceStamp(snapshotStamp)

    assertThat(beforeFlush).isEqualTo(model.startOffset)
    assertThat(afterFlush).isEqualTo(model.startOffset)
  }

  // --- edges -------------------------------------------------------------------------------------------------------

  @Test
  fun `repeated changes in the same region collapse instead of evicting, so old snapshots still resolve`() = withFixture {
    // Many changes rewriting the same region (like a console progress bar) all share offset 0, so the monotonic
    // history collapses them to a single entry and never overflows. No eviction => no dropped batches.
    var lastStamp = 0L
    val count = (TerminalOutputModelChangesTracker.MAX_CHANGES_HISTORY_LENGTH * 1.5).toInt()
    repeat(count) { i ->
      model.updateContent(0, "v$i")
      lastStamp = model.modificationStamp
      tracker.getFirstChangedLineAndReset()
    }

    // The most recent snapshot is up to date (converged).
    assertThat(tracker.getFirstChangedOffsetSinceStamp(lastStamp)).isEqualTo(model.endOffset)
    // An ancient snapshot is still resolvable (not dropped): the single retained change starts at the model start.
    assertThat(tracker.getFirstChangedOffsetSinceStamp(0L)).isEqualTo(model.startOffset)
  }

  @Test
  fun `drops the batch when a change newer than the stamp was evicted from the bounded history`() = withFixture {
    // Strictly increasing offsets (like `cat`-ing a large file): nothing collapses, so the bounded history
    // (MAX_CHANGES_HISTORY_LENGTH = 200) overflows and the oldest changes are evicted.
    var staleStamp = 0L
    var latestStamp = 0L
    val count = (TerminalOutputModelChangesTracker.MAX_CHANGES_HISTORY_LENGTH * 1.5).toInt()
    repeat(count) { i -> // well past the history limit to force eviction
      model.updateContent(i.toLong(), "x") // append one line at a strictly greater offset
      tracker.getFirstChangedLineAndReset()
      if (i == 0) staleStamp = model.modificationStamp
      latestStamp = model.modificationStamp
    }

    // The stale snapshot predates changes that have since been evicted: the true first-changed offset can no longer
    // be computed, so the batch must be dropped (null) rather than risk applying stale links.
    assertThat(tracker.getFirstChangedOffsetSinceStamp(staleStamp)).isNull()
    // A recent snapshot is still fully covered by the retained history and resolves normally.
    assertThat(tracker.getFirstChangedOffsetSinceStamp(latestStamp)).isEqualTo(model.endOffset)
  }

  @Test
  fun `keeps the reported line and offset valid across trimming`() = withFixture(maxLength = 8) {
    // Overflow the max length so the model trims whole lines from the start (advancing firstLineIndex / startOffset).
    // The change was recorded against the pre-trim line 0, which no longer exists: it must be clamped into the model.
    model.updateContent(0, "aa\nbb\ncc\ndd\nee") // 14 chars > 8
    assertThat(model.firstLineIndex).isGreaterThan(TerminalLineIndex.of(0)) // sanity: trimming actually happened

    assertThat(tracker.getFirstChangedLineAndReset()).isEqualTo(model.firstLineIndex)
    assertThat(tracker.getFirstChangedOffsetSinceStamp(0L)).isEqualTo(model.startOffset)
  }

  private fun withFixture(maxLength: Int = 0, block: Fixture.() -> Unit) {
    val model = TerminalTestUtil.createOutputModel(maxLength)
    val disposable = Disposer.newDisposable()
    try {
      val fixture = Fixture(model, TerminalOutputModelChangesTracker(model, disposable))
      fixture.block()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private class Fixture(val model: MutableTerminalOutputModel, val tracker: TerminalOutputModelChangesTracker)
}
