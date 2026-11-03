// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.idea.TestFor
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isSuccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Subsystems.PackagingRequirements
@Layers.Functional
@TestFor(issues = ["PY-91735"])
internal class PixiPackageSearchTest {
  @Test
  fun `no matches is an empty search rather than a command error`() {
    val output = EelProcessExecutionResult(1, byteArrayOf(), "No packages found matching missing".toByteArray())
    assertThat(PIXI_SEARCH_OUTPUT_TRANSFORMER(output).getOrThrow()).isEqualTo("{}")
  }

  @ParameterizedTest
  @ValueSource(strings = ["Unauthorized", "Failed to download repodata", "Connection refused"])
  fun `a repository failure is not a successful empty search`(message: String) {
    val output = EelProcessExecutionResult(1, byteArrayOf(), message.toByteArray())
    assertThat(PIXI_SEARCH_OUTPUT_TRANSFORMER(output).isSuccess).isFalse()
  }

  @Test
  fun `search uses the active feature channels and the matching platform name`() {
    val context = parsePixiPackageSearchContext(INFO, "analysis").getOrThrow()
    assertThat(context.platform).isEqualTo("custom-linux")
    assertThat(context.channels).containsExactly("https://example.org/private", "conda-forge")
    assertThat(context.targetSelectors).containsExactlyInAnyOrder("custom-linux", "linux-64", "linux", "unix")
    assertThat(context.arguments).containsExactly(
      "--platform=custom-linux", "--channel=https://example.org/private", "--channel=conda-forge",
    )
    assertThat(parsePixiPackageSearchContext(INFO, "default").getOrThrow().channels).containsExactly("conda-forge")
  }

  @Test
  fun `search rejects a deleted environment`() {
    assertThat(parsePixiPackageSearchContext(INFO, "deleted").isSuccess).isFalse()
  }

  @Test
  fun `search rejects an unsupported host platform`() {
    assertThat(parsePixiPackageSearchContext(INFO.replace("\"platform\":\"linux-64\"", "\"platform\":\"win-64\""),
                                           "analysis").isSuccess).isFalse()
  }

  @Test
  fun `search uses an installed non-host platform instead of the host`() {
    val output = INFO.replace("\"name\":\"analysis\",", """"name":"analysis","resolved_platform":{"name":"emulated","subdir":"linux-aarch64"},""")
    assertThat(parsePixiPackageSearchContext(output, "analysis").getOrThrow().platform).isEqualTo("emulated")
    assertThat(parsePixiPackageSearchContext(output, "analysis").getOrThrow().targetSelectors)
      .containsExactlyInAnyOrder("emulated", "linux-aarch64", "linux", "unix")
  }

  @Test
  fun `search does not guess between named platforms with the same subdir`() {
    val output = INFO.replace("{\"name\":\"custom-linux\",\"subdir\":\"linux-64\"}",
                              """{"name":"first","subdir":"linux-64"},{"name":"second","subdir":"linux-64"}""")
    assertThat(parsePixiPackageSearchContext(output, "analysis").isSuccess).isFalse()
  }

  @ParameterizedTest
  @ValueSource(strings = ["invalid", "[]", "{}", "{\"platform\":\"linux-64\",\"environments_info\":[{}]}"])
  fun `search rejects malformed environment information`(output: String) {
    assertThat(parsePixiPackageSearchContext(output, "default").isSuccess).isFalse()
  }
}

private val INFO = """
  {"platform":"linux-64","environments_info":[
    {"name":"default","channels":["conda-forge"],"platforms":[{"name":"linux-64","subdir":"linux-64"}]},
    {"name":"analysis","channels":["https://example.org/private","conda-forge"],"platforms":[
      {"name":"osx-arm64","subdir":"osx-arm64"},{"name":"custom-linux","subdir":"linux-64"}
    ]}
  ]}
""".trimIndent()
