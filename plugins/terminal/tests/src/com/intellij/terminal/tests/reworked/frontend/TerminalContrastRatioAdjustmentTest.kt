package com.intellij.terminal.tests.reworked.frontend

import com.intellij.ui.ColorUtil
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.junit.Test
import java.awt.Color

/**
 * Test for [org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils.ensureContrastRatio]
 */
internal class TerminalContrastRatioAdjustmentTest {
  @Test
  fun `grey on black background`() {
    doTest(bg = 0x000000, fg = 0x606060, ratio = 1f, expectedFg = 0x606060)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 2f, expectedFg = 0x606060)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 3f, expectedFg = 0x606060)

    doTest(bg = 0x000000, fg = 0x606060, ratio = 4f, expectedFg = 0x707070)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 5f, expectedFg = 0x7f7f7f)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 6f, expectedFg = 0x8c8c8c)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 7f, expectedFg = 0x989898)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 8f, expectedFg = 0xa3a3a3)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 9f, expectedFg = 0xadadad)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 10f, expectedFg = 0xb6b6b6)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 11f, expectedFg = 0xbebebe)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 12f, expectedFg = 0xc5c5c5)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 13f, expectedFg = 0xd1d1d1)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 14f, expectedFg = 0xd6d6d6)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 15f, expectedFg = 0xdbdbdb)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 16f, expectedFg = 0xe3e3e3)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 17f, expectedFg = 0xe9e9e9)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 18f, expectedFg = 0xeeeeee)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 19f, expectedFg = 0xf4f4f4)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 20f, expectedFg = 0xfafafa)
    doTest(bg = 0x000000, fg = 0x606060, ratio = 21f, expectedFg = 0xffffff)
  }

  @Test
  fun `grey on white background`() {
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 1f, expectedFg = 0x606060)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 2f, expectedFg = 0x606060)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 3f, expectedFg = 0x606060)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 4f, expectedFg = 0x606060)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 5f, expectedFg = 0x606060)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 6f, expectedFg = 0x606060)

    doTest(bg = 0xffffff, fg = 0x606060, ratio = 7f, expectedFg = 0x565656)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 8f, expectedFg = 0x4d4d4d)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 9f, expectedFg = 0x454545)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 10f, expectedFg = 0x3e3e3e)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 11f, expectedFg = 0x373737)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 12f, expectedFg = 0x313131)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 13f, expectedFg = 0x313131)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 14f, expectedFg = 0x272727)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 15f, expectedFg = 0x232323)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 16f, expectedFg = 0x1f1f1f)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 17f, expectedFg = 0x1b1b1b)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 18f, expectedFg = 0x151515)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 19f, expectedFg = 0x101010)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 20f, expectedFg = 0x080808)
    doTest(bg = 0xffffff, fg = 0x606060, ratio = 21f, expectedFg = 0x000000)
  }

  private fun doTest(bg: Int, fg: Int, ratio: Float, expectedFg: Int) {
    val expectedFgColor = Color(expectedFg)
    val actualFgColor = TerminalUiUtils.ensureContrastRatio(Color(bg), Color(fg), ratio)
    assertThat(actualFgColor)
      .overridingErrorMessage { "Expected #${ColorUtil.toHex(expectedFgColor)}, but was #${ColorUtil.toHex(actualFgColor)}" }
      .isEqualTo(expectedFgColor)
  }
}