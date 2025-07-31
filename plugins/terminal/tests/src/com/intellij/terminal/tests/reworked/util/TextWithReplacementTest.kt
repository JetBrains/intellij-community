// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.util

import com.intellij.terminal.session.StyleRange
import com.intellij.terminal.tests.reworked.util.TextWithReplacement.Companion.STYLE1
import com.intellij.terminal.tests.reworked.util.TextWithReplacement.Companion.STYLE2
import com.intellij.terminal.tests.reworked.util.TextWithReplacement.Companion.STYLE3
import com.intellij.terminal.tests.reworked.util.TextWithReplacement.Companion.STYLE4
import com.jediterm.terminal.TextStyle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class TextWithReplacementTest {
  @Test
  fun `parser self-tests`() {
    assertThat(parseTextWithReplacement(""))
      .isEqualTo(TextWithReplacement(
        TextWithStyles("", emptyList()),
        TextWithStyles("", emptyList()),
        0,
        0,
        TextWithStyles("", emptyList()),
      ))
    assertThat(parseTextWithReplacement("ab<|qwer>12345"))
      .isEqualTo(TextWithReplacement(
        TextWithStyles("ab12345", emptyList()),
        TextWithStyles("abqwer12345", emptyList()),
        2,
        0,
        TextWithStyles("qwer", emptyList()),
      ))
    assertThat(parseTextWithReplacement("ab<qwer|>12345"))
      .isEqualTo(TextWithReplacement(
        TextWithStyles("abqwer12345", emptyList()),
        TextWithStyles("ab12345", emptyList()),
        2,
        4,
        TextWithStyles("", emptyList()),
      ))
    assertThat(parseTextWithReplacement("ab<qwer|xyz>12345"))
      .isEqualTo(TextWithReplacement(
        TextWithStyles("abqwer12345", emptyList()),
        TextWithStyles("abxyz12345", emptyList()),
        2,
        4,
        TextWithStyles("xyz", emptyList()),
      ))
    assertThat(parseTextWithReplacement("[ab](1)<|[cd](2)>1[2345](3)"))
      .isEqualTo(TextWithReplacement(
        TextWithStyles("ab12345", listOf(
          styleRange(0, 2, STYLE1),
          styleRange(3, 7, STYLE3),
        )),
        TextWithStyles("abcd12345", listOf(
          styleRange(0, 2, STYLE1),
          styleRange(2, 4, STYLE2),
          styleRange(5, 9, STYLE3),
        )),
        2,
        0,
        TextWithStyles("cd", listOf(styleRange(0, 2, STYLE2))),
      ))
    assertThat(parseTextWithReplacement("[ab](1)<[cd](2)|>1[2345](3)"))
      .isEqualTo(TextWithReplacement(
        TextWithStyles("abcd12345", listOf(
          styleRange(0, 2, STYLE1),
          styleRange(2, 4, STYLE2),
          styleRange(5, 9, STYLE3),
        )),
        TextWithStyles("ab12345", listOf(
          styleRange(0, 2, STYLE1),
          styleRange(3, 7, STYLE3),
        )),
        2,
        2,
        TextWithStyles("", emptyList()),
      ))
    assertThat(parseTextWithReplacement("[ab](1)<[cd](2)|[xyz](3)>1[2345](4)"))
      .isEqualTo(TextWithReplacement(
        TextWithStyles("abcd12345", listOf(
          styleRange(0, 2, STYLE1),
          styleRange(2, 4, STYLE2),
          styleRange(5, 9, STYLE4),
        )),
        TextWithStyles("abxyz12345", listOf(
          styleRange(0, 2, STYLE1),
          styleRange(2, 5, STYLE3),
          styleRange(6, 10, STYLE4),
        )),
        2,
        2,
        TextWithStyles("xyz", listOf(styleRange(0, 3, STYLE3))),
      ))
    assertThat(parseTextWithReplacement("[ab<cd](1)|>[12345](2)"))
      .isEqualTo(TextWithReplacement(
        TextWithStyles("abcd12345", listOf(
          styleRange(0, 4, STYLE1),
          styleRange(4, 9, STYLE2),
        )),
        TextWithStyles("ab12345", listOf(
          styleRange(0, 2, STYLE1),
          styleRange(2, 7, STYLE2),
        )),
        2,
        2,
        TextWithStyles("", emptyList()),
      ))
    assertThat(parseTextWithReplacement("[ab<cd|ef>1](1)[2345](2)"))
      .isEqualTo(TextWithReplacement(
        TextWithStyles("abcd12345", listOf(
          styleRange(0, 5, STYLE1),
          styleRange(5, 9, STYLE2),
        )),
        TextWithStyles("abef12345", listOf(
          styleRange(0, 2, STYLE1),
          styleRange(4, 5, STYLE1),
          styleRange(5, 9, STYLE2),
        )),
        2,
        2,
        TextWithStyles("ef", emptyList()),
      ))
    assertThat(parseTextWithReplacement("[ab<cd|efgh>1](1)[2345](2)"))
      .isEqualTo(TextWithReplacement(
        TextWithStyles("abcd12345", listOf(
          styleRange(0, 5, STYLE1),
          styleRange(5, 9, STYLE2),
        )),
        TextWithStyles("abefgh12345", listOf(
          styleRange(0, 2, STYLE1),
          styleRange(6, 7, STYLE1),
          styleRange(7, 11, STYLE2),
        )),
        2,
        2,
        TextWithStyles("efgh", emptyList()),
      ))
    assertThat(parseTextWithReplacement("[ab<cdef|gh>1](1)[2345](2)"))
      .isEqualTo(TextWithReplacement(
        TextWithStyles("abcdef12345", listOf(
          styleRange(0, 7, STYLE1),
          styleRange(7, 11, STYLE2),
        )),
        TextWithStyles("abgh12345", listOf(
          styleRange(0, 2, STYLE1),
          styleRange(4, 5, STYLE1),
          styleRange(5, 9, STYLE2),
        )),
        2,
        4,
        TextWithStyles("gh", emptyList()),
      ))
    assertThat(parseTextWithReplacement("[ab<cdef|[gh](3)>1](1)[2345](2)"))
      .isEqualTo(TextWithReplacement(
        TextWithStyles("abcdef12345", listOf(
          styleRange(0, 7, STYLE1),
          styleRange(7, 11, STYLE2),
        )),
        TextWithStyles("abgh12345", listOf(
          styleRange(0, 2, STYLE1),
          styleRange(2, 4, STYLE3),
          styleRange(4, 5, STYLE1),
          styleRange(5, 9, STYLE2),
        )),
        2,
        4,
        TextWithStyles("gh", listOf(styleRange(0, 2, STYLE3))),
      ))
    assertThat(parseTextWithReplacement("<[ab|>cde](1)"))
      .isEqualTo(TextWithReplacement(
        TextWithStyles("abcde", listOf(
          styleRange(0, 5, STYLE1),
        )),
        TextWithStyles("cde", listOf(
          styleRange(0, 3, STYLE1),
        )),
        0,
        2,
        TextWithStyles("", emptyList()),
      ))
    assertThat(parseTextWithReplacement("<a[b|>cde](1)"))
      .isEqualTo(TextWithReplacement(
        TextWithStyles("abcde", listOf(
          styleRange(1, 5, STYLE1),
        )),
        TextWithStyles("cde", listOf(
          styleRange(0, 3, STYLE1),
        )),
        0,
        2,
        TextWithStyles("", emptyList()),
      ))
  }
}

private fun styleRange(start: Int, end: Int, style: TextStyle): StyleRange =
  StyleRange(start.toLong(), end.toLong(), style)
