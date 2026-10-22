// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The in-band size report (mode 2048) and the replies to the `XTWINOPS` size queries
 * `CSI 14/16/18/19 t`. The embedder gives the cell pixel size at creation and with
 * [TerminalEmulator.resize]; without it, each pixel field is 0.
 */
class SizeReportTest {

  @Test
  fun modeQueryReportsInBandSizeReportsAsSupported() = session(10, 3) { session ->
    session.write(csi($$"?2048$p"))  // DECRQM
    session.write(csi("?2048h"))
    session.write(csi($$"?2048$p"))
    // 2 = reset, 1 = set; enabling the mode sends a report in between
    session.assertResponses(csi($$"?2048;2$y"), csi("48;3;10;0;0t"), csi($$"?2048;1$y"))
  }

  @Test
  fun enablingTheModeReportsTheCurrentSize() = session(10, 3) { session ->
    session.write(csi("?2048h"))
    session.assertResponses(csi("48;3;10;0;0t"))
  }

  @Test
  fun enablingTheModeReportsTheCellPixelSize() = session(10, 3) { session ->
    session.resize(10, 3, CellPixelSize(width = 8, height = 16))
    session.write(csi("?2048h"))
    // CSI 48 ; rows ; columns ; height_px ; width_px t, with 3 * 16 and 10 * 8 pixels
    session.assertResponses(csi("48;3;10;48;80t"))
  }

  @Test
  fun queriesReportTheInitialCellPixelSizeBeforeAnyResize() = session(10, 3, initialCellPixelSize = CellPixelSize(8, 16)) { session ->
    session.write(csi("?2048h"))
    session.write(csi("16t"))
    session.assertResponses(csi("48;3;10;48;80t"), csi("6;16;8t"))
  }

  @Test
  fun resizeReportsTheNewSizeWhileTheModeIsOn() = session(10, 3) { session ->
    session.resize(20, 5, CellPixelSize(width = 8, height = 16))
    session.assertResponses()

    session.write(csi("?2048h"))
    session.resize(10, 3, CellPixelSize(width = 8, height = 16))
    // A resize reports even when only the cell pixel size changes.
    session.resize(10, 3, CellPixelSize(width = 9, height = 18))
    session.assertResponses(csi("48;5;20;80;160t"), csi("48;3;10;48;80t"), csi("48;3;10;54;90t"))

    session.write(csi("?2048l"))
    session.resize(20, 5)
    session.assertResponses(csi("48;5;20;80;160t"), csi("48;3;10;48;80t"), csi("48;3;10;54;90t"))
  }

  @Test
  fun xtwinopsQueriesReportTheLastSize() = session(10, 3) { session ->
    session.write(csi("18t"))
    session.resize(20, 5, CellPixelSize(width = 8, height = 16))
    session.write(csi("14t"))  // the text area in pixels: CSI 4 ; height_px ; width_px t
    session.write(csi("16t"))  // one cell in pixels: CSI 6 ; cell_height ; cell_width t
    session.write(csi("18t"))  // the text area in cells: CSI 8 ; rows ; columns t
    session.write(csi("19t"))  // the screen in cells: CSI 9 ; rows ; columns t
    session.assertResponses(csi("8;3;10t"), csi("4;80;160t"), csi("6;16;8t"), csi("8;5;20t"), csi("9;5;20t"))
  }

  @Test
  fun xtwinopsPixelQueriesReportZeroWithoutCellPixelSize() = session(10, 3) { session ->
    session.write(csi("14t"))
    session.write(csi("16t"))
    session.assertResponses(csi("4;0;0t"), csi("6;0;0t"))
  }

  @Test
  fun queriesReportTheColumnCountThatDeccolmSet() = session(10, 3, initialCellPixelSize = CellPixelSize(8, 16)) { session ->
    session.write(csi("?40h"))   // allow DECCOLM
    session.write(csi("?3h"))    // DECCOLM: the library itself resizes to 132 columns
    assertThat(session.emulator.size).isEqualTo(TerminalSize(132, 3))
    session.write(csi("18t"))    // XTWINOPS query for the text area in cells
    session.write(csi("?2048h")) // enabling the in-band size reports sends one report at once
    session.assertResponses(csi("8;3;132t"), csi("48;3;132;48;1056t"))
  }
}
