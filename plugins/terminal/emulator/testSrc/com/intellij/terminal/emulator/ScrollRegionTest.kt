// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.junit.jupiter.api.Test

/**
 * The scroll region (DECSTBM, `CSI top;bottom r`): top/bottom margins that confine scrolling to a band
 * within the screen, so content outside the region is left untouched. Distinct from the scrollback
 * buffer (see [ScrollbackBufferTest]).
 */
class ScrollRegionTest {

  @Test
  fun noScrollWhenOutsideScrollRegion() = session(80, 5) { session ->
    session.write(csi("1;3r"))                    // scroll region rows 1..3
    session.write("Line 1\r\nLine 2\r\nLine 3")
    session.write(csi("4;1H") + "Line 4")         // row 4, outside the region
    session.write("\r\nLine 5")
    session.assertScreenLines("Line 1", "Line 2", "Line 3", "Line 4", "Line 5")

    session.write("\r\nLine 6")                   // replaces the last line; region 1..3 untouched
    session.assertScreenLines("Line 1", "Line 2", "Line 3", "Line 4", "Line 6")
  }
}
