// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@Subsystems.PackagingRequirements
@Layers.Functional
@TestFor(issues = ["PY-91735"])
internal class PixiCondaVersionTest {
  @ParameterizedTest
  @CsvSource(
    "1.1a1, 1.1.0dev1", "1.1.0post1, 1.1post1", "1.0+abc.2, 1.0+abc.10",
    "1.0.1_, 1.0.1a", "1.0.1a, 1.0.1", "9999, 1!0.1", "1.0dev1, 1.0_",
    "1.0dev1, 1.0devdev1", "1.0postpost1, 1.0post1", "1.0+abc, 1.0",
    "1.0.99999999999999999999, 1.0.100000000000000000000",
  )
  fun `native versions use Conda ordering`(older: String, newer: String) {
    assertThat(PIXI_CONDA_VERSION_COMPARATOR.compare(older, newer)).isNegative()
    assertThat(PIXI_CONDA_VERSION_COMPARATOR.compare(newer, older)).isPositive()
  }

  @ParameterizedTest
  @CsvSource("1.1, 1.1.0", "1.0_0, 1.0.0", "1.0_, 1.0-", "1__, 1._", "0!1.0, 1", "1.0A1, 1.0a1")
  fun `native aliases compare equally`(first: String, second: String) {
    assertThat(PIXI_CONDA_VERSION_COMPARATOR.compare(first, second)).isZero()
    assertThat(PIXI_CONDA_VERSION_COMPARATOR.compare(second, first)).isZero()
  }

  @Test
  fun `malformed values form one category before valid versions`() {
    val invalid = listOf("1___", "1..0", "1_0+1-0", "1!2!3", "invalid?")
    for (value in invalid) {
      assertThat(PIXI_CONDA_VERSION_COMPARATOR.compare(value, "0")).isNegative()
      assertThat(PIXI_CONDA_VERSION_COMPARATOR.compare("0", value)).isPositive()
    }
    val sorted = invalid.sortedWith(PIXI_CONDA_VERSION_COMPARATOR)
    assertThat(sorted).containsExactlyElementsOf(invalid.sorted())
  }

  @ParameterizedTest
  @CsvSource("1.1a1, true", "1.1.post1, true", "1.0.1_, false", "1.1post1, false", "1.1.0dev1, false")
  fun `explicit versions retain their Conda meaning through the dialog`(version: String, selectable: Boolean) {
    assertThat(isPixiNativeVersionSelectable(version)).isEqualTo(selectable)
  }
}
