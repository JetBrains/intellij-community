// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.uv

import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.sdk.uv.impl.parsePinnedVersions
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.uv.lowestPython
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The two inputs of "Update all outdated packages" in a uv project: the `uv pip compile` pins and the lowest Python. */
class UvUpdateToLatestParsingTest {
  @Test
  fun `pins of the compile output are keyed by the normalized name`() {
    val output = """
      certifi==2026.10.23
      Charset_Normalizer==3.5.2
      requests[socks]==2.32.3
      urllib3==2.6.3
    """.trimIndent()

    assertThat(parsePinnedVersions(output)).containsExactlyInAnyOrderEntriesOf(mapOf(
      PyPackageName.from("certifi") to "2026.10.23",
      PyPackageName.from("charset-normalizer") to "3.5.2",
      PyPackageName.from("requests") to "2.32.3",
      PyPackageName.from("urllib3") to "2.6.3",
    ))
  }

  @Test
  fun `a package with a fork by the Python version has no pin`() {
    val output = """
      certifi==2026.10.23
      urllib3==2.6.3 ; python_full_version < '3.10'
      urllib3==2.8.0 ; python_full_version >= '3.10'
    """.trimIndent()

    assertThat(parsePinnedVersions(output)).containsExactlyEntriesOf(mapOf(PyPackageName.from("certifi") to "2026.10.23"))
  }

  @Test
  fun `a marker on a single pin keeps the pin`() {
    assertThat(parsePinnedVersions("colorama==0.4.6 ; sys_platform == 'win32'"))
      .containsExactlyEntriesOf(mapOf(PyPackageName.from("colorama") to "0.4.6"))
  }

  @Test
  fun `the lowest Python is the lower bound of requires-python`() {
    assertThat(lowestPython(">=3.9")).isEqualTo(LanguageLevel.PYTHON39)
    assertThat(lowestPython(">=3.10,<4")).isEqualTo(LanguageLevel.PYTHON310)
    assertThat(lowestPython("~=3.11")).isEqualTo(LanguageLevel.PYTHON311)
    assertThat(lowestPython(">3.9")).isEqualTo(LanguageLevel.PYTHON310)
  }

  @Test
  fun `requires-python without a lower bound starts at the oldest supported Python 3`() {
    assertThat(lowestPython("<4")).isEqualTo(LanguageLevel.PYTHON36)
  }

  @Test
  fun `requires-python that accepts no supported Python gives no version`() {
    assertThat(lowestPython(">=4")).isNull()
  }
}