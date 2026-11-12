// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.intellij.idea.TestFor
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.Result
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test

@TestFor(classes = [PipParseUtils::class], issues = ["PY-93912"])
@TestApplication
@Subsystems.PackagingRequirements
@Layers.Functional
internal class PipParseUtilsTest {
  @Test
  fun `outdated packages come from the JSON list`() {
    val result = PipParseUtils.parseOutdatedOutputs("""[{"name": "requests", "version": "2.0.0", "latest_version": "2.32.3"}]""")

    assertThat(result.orThrow())
      .extracting({ it.name }, { it.version }, { it.latestVersion })
      .containsExactly(tuple("requests", "2.0.0", "2.32.3"))
  }

  @Test
  fun `outdated packages come from the JSON list after the log lines of failed fetches`() {
    val output = """
      Could not fetch URL https://pypi.example/simple/requests/: There was a problem confirming the ssl certificate - skipping
      Could not fetch URL https://pypi.example/simple/pip/: There was a problem confirming the ssl certificate - skipping
      [{"name": "requests", "version": "2.0.0", "latest_version": "2.32.3", "latest_filetype": "wheel"}]
    """.trimIndent()

    val result = PipParseUtils.parseOutdatedOutputs(output)

    assertThat(result.orThrow())
      .extracting({ it.name }, { it.version }, { it.latestVersion })
      .containsExactly(tuple("requests", "2.0.0", "2.32.3"))
  }

  @Test
  fun `log lines with no JSON list are a failure that holds the output`() {
    val output = "Could not fetch URL https://pypi.example/simple/pip/: There was a problem confirming the ssl certificate - skipping"

    val result = PipParseUtils.parseOutdatedOutputs(output)

    assertThat(result).isInstanceOf(Result.Failure::class.java)
    assertThat((result as Result.Failure).error.message).contains(output)
  }

  @Test
  fun `empty output is a failure`() {
    assertThat(PipParseUtils.parseOutdatedOutputs("")).isInstanceOf(Result.Failure::class.java)
  }
}
