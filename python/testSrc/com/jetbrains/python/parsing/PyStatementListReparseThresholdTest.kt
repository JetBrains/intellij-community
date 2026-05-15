package com.jetbrains.python.parsing

import com.jetbrains.python.psi.impl.PyStatementListElementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class PyStatementListReparseThresholdTest {

  // Production defaults - mirror constants in PyStatementListElementType.
  private val minFile = 20_000
  private val maxList = 10_000
  private val maxRatio = 10

  private fun shouldReparse(fileLength: Int, statementListLength: Int): Boolean =
    PyStatementListElementType.isLargeEnoughForIncrementalReparse(
      fileLength, statementListLength, minFile, maxList, maxRatio,
    )

  private fun shouldReparse(
    fileLength: Int,
    statementListLength: Int,
    minFileChars: Int,
    maxListChars: Int,
    maxRatioPercent: Int,
  ): Boolean =
    PyStatementListElementType.isLargeEnoughForIncrementalReparse(
      fileLength, statementListLength, minFileChars, maxListChars, maxRatioPercent,
    )

  @Test
  fun `small file - full reparse is cheap, incremental skipped`() {
    // 5K file (~150 lines), 200-char block grew to 201. File below the 20K floor.
    assertThat(shouldReparse(fileLength = 5_000, statementListLength = 201)).isFalse()
  }

  @Test
  fun `medium file, normal function - incremental fires`() {
    // 50K file (~1500 lines), 2K block grew to 2001. Well below ratio (5K) and abs cap (10K).
    assertThat(shouldReparse(fileLength = 50_000, statementListLength = 2_001)).isTrue()
  }

  @Test
  fun `medium file, function takes most of the file - ratio binds, incremental skipped`() {
    // 50K file, 5K block (10% of file) grew to 5001. Savings too small to justify the overhead.
    assertThat(shouldReparse(fileLength = 50_000, statementListLength = 5_001)).isFalse()
  }

  @Test
  fun `large file, normal function - incremental fires`() {
    // 500K file (~15K lines), 2K block grew to 2001. Abs cap (10K) not hit, huge savings.
    assertThat(shouldReparse(fileLength = 500_000, statementListLength = 2_001)).isTrue()
  }

  @Test
  fun `large file, huge function - abs cap binds to keep keystrokes responsive`() {
    // 500K file, 10K block grew to 10001. Lexing 10K per keystroke is already a lot.
    assertThat(shouldReparse(fileLength = 500_000, statementListLength = 10_001)).isFalse()
  }

  @Test
  fun `each threshold is plumbed independently`() {
    // Lowering minFile lets through what defaults reject (small file).
    assertThat(shouldReparse(5_000, 100, minFileChars = 1_000, maxListChars = 10_000, maxRatioPercent = 10)).isTrue()
    // Raising maxList lets through what defaults reject (block past abs cap in a large file).
    assertThat(shouldReparse(1_000_000, 20_000, minFileChars = 20_000, maxListChars = 50_000, maxRatioPercent = 10)).isTrue()
    // Tightening ratio rejects what defaults accept (block at exactly the default ratio).
    assertThat(shouldReparse(50_000, 5_000, minFileChars = 20_000, maxListChars = 10_000, maxRatioPercent = 5)).isFalse()
  }

  @Test
  fun `zero thresholds (clamped from negatives) degrade gracefully`() {
    // minFile=0: lower bound disabled, but maxList/ratio still bind.
    assertThat(shouldReparse(5_000, 50, minFileChars = 0, maxListChars = 10_000, maxRatioPercent = 10)).isTrue()
    // maxList=0 or ratio=0: only empty new text passes; no crash.
    assertThat(shouldReparse(50_000, 1, minFileChars = 0, maxListChars = 0, maxRatioPercent = 10)).isFalse()
    assertThat(shouldReparse(50_000, 1, minFileChars = 0, maxListChars = 10_000, maxRatioPercent = 0)).isFalse()
  }
}
