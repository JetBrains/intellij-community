// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isSuccess
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.CsvSource

@Subsystems.PackagingRequirements
@Layers.Functional
@TestFor(issues = ["PY-91735"])
internal class PixiOutdatedPackagesTest {
  @ParameterizedTest
  @ValueSource(strings = [
    "C:\\private channel\\probe-2.0-build_0.conda",
    "C:/private channel/probe-2.0-build_0.conda",
    "/private channel/probe-2.0-build_0.conda",
    "file:///C:/private%20channel/probe-2.0-build_0.conda",
  ])
  fun `solver updates accept local artifact paths on every host`(location: String) {
    val digest = "a".repeat(64)
    val jsonLocation = location.replace("\\", "\\\\")
    val output = """{"version":1,"environment":{"docs":{"win-64":[
      {"name":"probe","type":"conda","after":{"conda":"$jsonLocation","sha256":"$digest"}}
    ]}}}"""

    val candidates = parsePixiCompatibleUpdates(output, "docs", "win-64")
    assertThat(candidates.isSuccess).isTrue()
    assertThat(candidates.getOrThrow()).containsExactly(PixiNativePackage("probe", "2.0"))
    val artifacts = parsePixiActiveNativeArtifacts(output, "docs", "win-64", null)
    assertThat(artifacts.isSuccess).isTrue()
    assertThat(artifacts.getOrThrow()).containsOnlyKeys("probe")
    assertThat(artifacts.getOrThrow()["probe"]).isNotNull()
      .isEqualTo(createPixiNativeArtifact("2.0", "build_0", location, digest, null))
  }

  @Test
  fun `active artifacts retain unchanged native identities and ignore Python records`() {
    val digest = "a".repeat(64)
    val locked = """[
      {"name":"probe","version":"1.0","kind":"conda","build":"0","url":"https://example.org/probe-1.0-0.conda","sha256":"$digest"},
      {"name":"python-source","version":null,"kind":"pypi"}
    ]"""
    val result = parsePixiActiveNativeArtifacts(
      """{"version":1,"environment":{"docs":{"osx-arm64":[]}}}""", "docs", "osx-arm64", locked,
    ).getOrThrow()

    assertThat(result).containsOnlyKeys("probe")
    assertThat(result["probe"]).isEqualTo(createPixiNativeArtifact("1.0", "0", "https://example.org/probe-1.0-0.conda", digest, null))
  }

  @Test
  fun `active solver identities replace stale artifacts and remove deleted packages`() {
    val digest = "a".repeat(64)
    val locked = """[
      {"name":"probe","version":"9","kind":"conda"},
      {"name":"removed","version":"1","kind":"conda"}
    ]"""
    val output = """{"version":1,"environment":{"docs":{"osx-arm64":[
      {"name":"probe","type":"conda","after":{"conda":"https://example.org/probe-2.0-build_0.conda","sha256":"$digest"}},
      {"name":"removed","type":"conda","after":null}
    ]},"default":{"osx-arm64":[{"name":"wrong-env","type":"conda","after":{}}]}}}"""

    val result = parsePixiActiveNativeArtifacts(output, "docs", "osx-arm64", locked).getOrThrow()

    assertThat(result).containsOnlyKeys("probe")
    assertThat(result["probe"]).isEqualTo(
      createPixiNativeArtifact("2.0", "build_0", "https://example.org/probe-2.0-build_0.conda", digest, null),
    )
  }

  @Test
  fun `an active name stays present when its artifact metadata is unknown`() {
    val output = """{"version":1,"environment":{"docs":{"osx-arm64":[
      {"name":"new","type":"conda","after":{"conda":"https://example.org/new-2.0-0.conda"}},
      {"name":"replaced","type":"conda","after":{}}
    ]}}}"""
    val locked = """[{"name":"unchanged","version":"1.0","kind":"conda"}]"""

    assertThat(parsePixiActiveNativeArtifacts(output, "docs", "osx-arm64", locked).getOrThrow())
      .containsExactlyEntriesOf(mapOf("unchanged" to null, "new" to null, "replaced" to null))
  }

  @Test
  fun `a complete solver solution supplies active artifacts without a lock file`() {
    val digest = "b".repeat(32)
    val output = """{"version":1,"environment":{"docs":{"win-64":[
      {"name":"probe","type":"conda","after":{"conda":"https://example.org/probe-2.0-0.tar.bz2","md5":"$digest"}}
    ]}}}"""

    assertThat(parsePixiActiveNativeArtifacts(output, "docs", "win-64", null).getOrThrow()["probe"])
      .isEqualTo(createPixiNativeArtifact("2.0", "0", "https://example.org/probe-2.0-0.tar.bz2", null, digest))
  }

  @ParameterizedTest
  @ValueSource(strings = ["{}", "null", "not-json", "{\"version\":2,\"environment\":{}}"])
  fun `invalid solver metadata cannot prove active artifacts`(output: String) {
    assertThat(parsePixiActiveNativeArtifacts(output, "docs", "osx-arm64", null).isSuccess).isFalse()
  }

  @Test
  fun `invalid frozen metadata cannot prove active artifacts`() {
    assertThat(parsePixiActiveNativeArtifacts("""{"version":1,"environment":{}}""", "docs", "osx-arm64", "{}").isSuccess)
      .isFalse()
  }

  @Test
  fun `a solver downgrade replaces a higher stale lock version`() {
    val output = """{"version":1,"environment":{"docs":{"osx-arm64":[
      {"name":"probe","type":"conda","after":{"conda":"https://example.org/probe-1.5-build.conda"}}
    ]}}}"""

    val candidates = parsePixiCompatibleUpdates(output, "docs", "osx-arm64", listOf(PixiNativePackage("probe", "2.0")))
      .getOrThrow()

    assertThat(candidates).containsExactly(PixiNativePackage("probe", "1.5"))
    assertThat(findPixiNativePackageUpdate(PixiPackage("probe", "1.0", "conda"), candidates)?.latestVersion).isEqualTo("1.5")
  }

  @Test
  fun `a solver removal suppresses the stale lock version`() {
    val output = """{"version":1,"environment":{"docs":{"osx-arm64":[
      {"name":"probe","type":"conda","after":null}
    ]}}}"""

    val candidates = parsePixiCompatibleUpdates(output, "docs", "osx-arm64", listOf(PixiNativePackage("probe", "2.0")))
      .getOrThrow()

    assertThat(candidates).isEmpty()
    assertThat(findPixiNativePackageUpdate(PixiPackage("probe", "1.0", "conda"), candidates)).isNull()
  }

  @Test
  fun `an unchanged lock version remains an update candidate`() {
    val output = """{"version":1,"environment":{"docs":{"osx-arm64":[]}}}"""

    val candidates = parsePixiCompatibleUpdates(output, "docs", "osx-arm64", listOf(PixiNativePackage("probe", "2.0")))
      .getOrThrow()

    assertThat(candidates).containsExactly(PixiNativePackage("probe", "2.0"))
    assertThat(findPixiNativePackageUpdate(PixiPackage("probe", "1.0", "conda"), candidates)?.latestVersion).isEqualTo("2.0")
  }

  @Test
  fun `solver updates select only the requested environment and platform`() {
    val output = """{"version":1,"environment":{
      "docs":{"osx-arm64":[
        {"name":"raw_name","type":"conda","after":{"conda":"https://example.org/raw_name-1.2.3-build_0.conda"}},
        {"name":"old-format","type":"conda","after":{"conda":"https://example.org/old-format-2.0-build.tar.bz2"}},
        {"name":"removed","type":"conda","after":null},
        {"name":"pip-only","type":"pypi","after":{"version":"9"}}
      ],"win-64":[{"name":"wrong-platform","type":"conda","after":{"conda":"https://example.org/wrong-platform-9-build.conda"}}]},
      "default":{"osx-arm64":[{"name":"wrong-env","type":"conda","after":{"conda":"https://example.org/wrong-env-9-build.conda"}}]}
    }}"""
    assertThat(parsePixiCompatibleUpdates(output, "docs", "osx-arm64").getOrThrow()).containsExactly(
      PixiNativePackage("raw_name", "1.2.3"), PixiNativePackage("old-format", "2.0"),
    )
  }
  @Test
  fun `a native update uses the latest version of the exact Conda name`() {
    val result = findPixiNativePackageUpdate(PixiPackage("typing_extensions", "4.0.0", "conda"), listOf(
      PixiNativePackage("typing-extensions", "99"),
      PixiNativePackage("typing_extensions", "4.9.0"),
      PixiNativePackage("typing_extensions", "4.15.0"),
    ))
    assertThat(result).isNotNull()
    assertThat(result?.name).isEqualTo("typing-extensions")
    assertThat(result?.version).isEqualTo("4.0.0")
    assertThat(result?.latestVersion).isEqualTo("4.15.0")
  }

  @ParameterizedTest
  @ValueSource(strings = ["4.0.0", "3.0.0"])
  fun `an equal or older version is not an update`(version: String) {
    assertThat(findPixiNativePackageUpdate(PixiPackage("probe", "4.0.0", "conda"), listOf(PixiNativePackage("probe", version))))
      .isNull()
  }

  @Test
  fun `a fuzzy native result is not an update`() {
    assertThat(findPixiNativePackageUpdate(PixiPackage("probe", "1.0", "conda"), listOf(PixiNativePackage("probe_extra", "9.0"))))
      .isNull()
  }

  @Test
  fun `missing and PyPI versions are not native updates`() {
    val candidates = listOf(PixiNativePackage("probe", "9.0"))
    assertThat(findPixiNativePackageUpdate(PixiPackage("probe", null, "conda"), candidates)).isNull()
    assertThat(findPixiNativePackageUpdate(PixiPackage("probe", "1.0", "pypi"), candidates)).isNull()
    assertThat(findPixiNativePackageUpdate(PixiPackage("probe", "1.0", "conda"), emptyList())).isNull()
  }

  @ParameterizedTest
  @CsvSource("1.0+abc, 1.0", "1.0+abc.2, 1.0+abc.10")
  fun `native update detection does not use Python version ordering`(older: String, newer: String) {
    val update = findPixiNativePackageUpdate(PixiPackage("probe", older, "conda"), listOf(PixiNativePackage("probe", newer)))
    assertThat(update?.latestVersion).isEqualTo(newer)
    val downgrade = findPixiNativePackageUpdate(PixiPackage("probe", newer, "conda"), listOf(PixiNativePackage("probe", older)))
    assertThat(downgrade).isNull()
  }

  @ParameterizedTest
  @ValueSource(strings = ["1.0.1_", "1.1post1", "1.1.0dev1"])
  fun `an update cannot pin a different version after dialog normalization`(latest: String) {
    val update = findPixiNativePackageUpdate(PixiPackage("probe", "0.1", "conda"), listOf(
      PixiNativePackage("probe", "0.2"), PixiNativePackage("probe", latest),
    ))
    assertThat(update).isNull()
  }
}
