// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.hyperlinks

import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkId
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkInfo
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinkInfoDto
import org.jetbrains.plugins.terminal.hyperlinks.session.toDto
import org.jetbrains.plugins.terminal.hyperlinks.session.toFilterResultInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** The frontend knows whether a hyperlink names a file without the backend hyperlink info, which split mode drops. */
@TestApplication
class TerminalHyperlinkInfoDtoTest {
  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun theFileFlagSurvivesTheDtoRoundTrip(isFile: Boolean) {
    val hyperlink = TerminalHyperlinkInfo(
      id = TerminalHyperlinkId(5),
      hyperlinkInfo = null,
      absoluteStartOffset = 10,
      absoluteEndOffset = 20,
      style = null,
      followedStyle = null,
      hoveredStyle = null,
      isInvisibleLink = false,
      layer = 0,
      isFile = isFile,
    )

    val dto = hyperlink.toDto() as TerminalHyperlinkInfoDto
    assertThat(dto.isFile).isEqualTo(isFile)
    assertThat((dto.toFilterResultInfo() as TerminalHyperlinkInfo).isFile).isEqualTo(isFile)
  }
}
