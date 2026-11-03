// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@Subsystems.PackagingRequirements
@Layers.Functional
internal class PixiNativeArtifactTest {
  @ParameterizedTest
  @CsvSource(
    "C:\\private channel\\probe-1.0-0.conda, file:///C:/private%20channel/probe-1.0-0.conda",
    "C:/private channel/probe-1.0-0.conda, file:///C:/private%20channel/probe-1.0-0.conda",
    "/private channel/probe-1.0-0.conda, file:///private%20channel/probe-1.0-0.conda",
  )
  fun `local artifact identities do not depend on the host platform`(path: String, uri: String) {
    val local = createPixiNativeArtifact("1.0", "0", path, "a".repeat(64), null)
    val escaped = createPixiNativeArtifact("1.0", "0", uri, "a".repeat(64), null)

    assertThat(local).isNotNull()
    assertThat(escaped).isNotNull()
    assertThat(local?.matches(requireNotNull(escaped))).isTrue()
  }

  @ParameterizedTest
  @ValueSource(strings = ["", "abc", "g000000000000000000000000000000000000000000000000000000000000000"])
  fun `malformed digests cannot prove artifact identity`(digest: String) {
    assertThat(createPixiNativeArtifact("1.0", "0", "https://example.org/probe.conda", digest, null)).isNull()
    assertThat(createPixiNativeArtifact("1.0", "0", "https://example.org/probe.conda", null, digest)).isNull()
  }

  @Test
  fun `missing identity metadata cannot prove artifact identity`() {
    assertThat(createPixiNativeArtifact("1.0", null, "https://example.org/probe.conda", "a".repeat(64), null)).isNull()
    assertThat(createPixiNativeArtifact("1.0", "0", null, "a".repeat(64), null)).isNull()
    assertThat(createPixiNativeArtifact("1.0", "0", "https://example.org/probe.conda", null, null)).isNull()
    assertThat(createPixiNativeArtifact("1.0", "0", "unknown:probe", "a".repeat(64), null)).isNull()
  }

  @Test
  fun `digests ignore hexadecimal letter case`() {
    val lower = PixiNativeArtifact("1.0", "0", "source", "a".repeat(64), "b".repeat(32))
    val upper = lower.copy(sha256 = "A".repeat(64), md5 = "B".repeat(32))

    assertThat(lower.matches(upper)).isTrue()
    assertThat(lower.copy(sha256 = null).matches(upper.copy(sha256 = null))).isTrue()
  }

  @Test
  fun `matching MD5 cannot override a different SHA256`() {
    val first = PixiNativeArtifact("1.0", "0", "source", "a".repeat(64), "b".repeat(32))

    assertThat(first.matches(first.copy(sha256 = "c".repeat(64)))).isFalse()
  }
}
