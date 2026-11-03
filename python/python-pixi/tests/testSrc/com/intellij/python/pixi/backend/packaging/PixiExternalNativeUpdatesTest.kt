// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

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
internal class PixiExternalNativeUpdatesTest {
  @ParameterizedTest
  @ValueSource(strings = ["workspace", "project", "tool.pixi.workspace", "tool.pixi.project"])
  fun `native updates retain disabled priority from every manifest form`(table: String) {
    assertThat(parsePixiNativeChannelPriority("[$table]\nchannel-priority = 'disabled'").getOrThrow()).isEqualTo("disabled")
  }

  @Test
  fun `native updates use strict channel priority by default`() {
    assertThat(parsePixiNativeChannelPriority("[workspace]\nname = 'probe'").getOrThrow()).isEqualTo("strict")
    assertThat(parsePixiNativeChannelPriority("[workspace]\nchannel-priority = 'strict'").getOrThrow()).isEqualTo("strict")
  }

  @ParameterizedTest
  @ValueSource(strings = ["'flexible'", "1", "false", "[]"])
  fun `unsupported channel priority cannot authorize a native update`(priority: String) {
    assertThat(parsePixiNativeChannelPriority("[workspace]\nchannel-priority = $priority").isSuccess).isFalse()
  }

  @Test
  fun `an independent native update retains the exact requested name`() {
    val output = """{"success":true,"actions":{
      "LINK":[{"name":"raw_name","version":"2.0","depends":[]},{"name":"new-dependency","version":"1.0","depends":[]}],
      "UNLINK":[{"name":"raw_name","version":"1.0"}]
    }}"""

    val result = parsePixiExternalNativeUpdates(output, "raw_name", setOf("raw_name", "python"))

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).containsExactly(PixiNativePackage("raw_name", "2.0"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["LINK", "UNLINK"])
  fun `an external update cannot change an active Pixi artifact`(action: String) {
    val replacement = """{"name":"python","version":"9.0","depends":[]}"""
    val output = """{"success":true,"actions":{
      "LINK":[{"name":"probe","version":"2.0","depends":[]}${if (action == "LINK") ",$replacement" else ""}],
      "UNLINK":[${if (action == "UNLINK") replacement else ""}]
    }}"""

    val expected = artifact()
    val installed = listOf(PixiInstalledCondaPackage("python", "1.0", dependencies = emptyList(), artifact = expected))
    val result = parsePixiExternalNativeUpdates(output, "probe", setOf("probe", "python"), installed, mapOf("python" to expected))

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `an external native update can replace another external dependency`() {
    val output = """{"success":true,"actions":{
      "LINK":[{"name":"probe","version":"2.0","depends":["dependency >=2"]},
              {"name":"dependency","version":"2.0","depends":[]}],
      "UNLINK":[{"name":"probe","version":"1.0"},{"name":"dependency","version":"1.0"}]
    }}"""
    val installed = listOf(PixiInstalledCondaPackage("dependency", "1.0", dependencies = emptyList()))

    val result = parsePixiExternalNativeUpdates(output, "probe", setOf("probe", "dependency"), installed)

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).containsExactly(PixiNativePackage("probe", "2.0"))
  }

  @Test
  fun `a removed external dependency cannot remain in the proposed graph`() {
    val output = """{"success":true,"actions":{
      "LINK":[{"name":"probe","version":"2.0","depends":["dependency >=1"]}],
      "UNLINK":[{"name":"dependency","version":"1.0"}]
    }}"""
    val installed = listOf(PixiInstalledCondaPackage("dependency", "1.0", dependencies = emptyList()))

    val result = parsePixiExternalNativeUpdates(output, "probe", setOf("probe", "dependency"), installed)

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `an external removal cannot break another unchanged installed package`() {
    val output = """{"success":true,"actions":{
      "LINK":[{"name":"probe","version":"2.0","depends":[]}],
      "UNLINK":[{"name":"dependency","version":"1.0"}]
    }}"""
    val installed = listOf(
      PixiInstalledCondaPackage("dependency", "1.0", dependencies = emptyList()),
      PixiInstalledCondaPackage("unchanged", "1.0", dependencies = listOf("dependency >=1")),
    )

    val result = parsePixiExternalNativeUpdates(output, "probe", setOf("probe", "dependency", "unchanged"), installed)

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `an external update can remove an obsolete external dependency`() {
    val output = """{"success":true,"actions":{
      "LINK":[{"name":"probe","version":"2.0","depends":[]}],
      "UNLINK":[{"name":"dependency","version":"1.0"}]
    }}"""
    val installed = listOf(PixiInstalledCondaPackage("dependency", "1.0", dependencies = emptyList()))

    val result = parsePixiExternalNativeUpdates(output, "probe", setOf("probe", "dependency"), installed)

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).containsExactly(PixiNativePackage("probe", "2.0"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["LINK", "UNLINK"])
  fun `an external native transaction cannot replace a package overwritten by pip`(action: String) {
    val replaced = """{"name":"six","version":"1.17","depends":[]}"""
    val output = """{"success":true,"actions":{
      "LINK":[{"name":"probe","version":"2.0","depends":[]}${if (action == "LINK") ",$replaced" else ""}],
      "UNLINK":[${if (action == "UNLINK") replaced else ""}]
    }}"""
    val rawInventory = listOf(PixiInstalledCondaPackage("six", "1.16", dependencies = emptyList()))

    val result = parsePixiExternalNativeUpdates(output, "probe", setOf("probe"), rawInventory)

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @ParameterizedTest
  @ValueSource(strings = ["\"depends\":[\"__cuda >=99\"]", "\"depends\":null"])
  fun `a replacement external dependency must retain a complete compatible graph`(dependency: String) {
    val output = """{"success":true,"actions":{
      "LINK":[{"name":"probe","version":"2.0","depends":["dependency >=2"]},
              {"name":"dependency","version":"2.0",$dependency}],
      "UNLINK":[{"name":"dependency","version":"1.0"}]
    }}"""
    val installed = listOf(PixiInstalledCondaPackage("dependency", "1.0", dependencies = emptyList()))

    val result = parsePixiExternalNativeUpdates(output, "probe", setOf("probe", "dependency"), installed)

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `an external update cannot introduce a virtual package requirement`() {
    val output = """{"success":true,"actions":{
      "LINK":[{"name":"probe","version":"2.0","depends":["__cuda >=99"]}],
      "UNLINK":[{"name":"probe","version":"1.0"}]
    }}"""

    val result = parsePixiExternalNativeUpdates(output, "probe", setOf("probe", "python"))

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `a new dependency cannot introduce a virtual package requirement`() {
    val output = """{"success":true,"actions":{
      "LINK":[{"name":"probe","version":"2.0","depends":["new-dependency"]},
              {"name":"new-dependency","version":"1.0","depends":["__cuda >=99"]}],
      "UNLINK":[{"name":"probe","version":"1.0"}]
    }}"""

    val result = parsePixiExternalNativeUpdates(output, "probe", setOf("probe", "python"))

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @ParameterizedTest
  @ValueSource(strings = ["", ",\"depends\":null"])
  fun `unknown dependency metadata cannot authorize a native update`(dependencyField: String) {
    val output = """{"success":true,"actions":{
      "LINK":[{"name":"probe","version":"2.0"$dependencyField}],
      "UNLINK":[{"name":"probe","version":"1.0"}]
    }}"""

    val result = parsePixiExternalNativeUpdates(output, "probe", setOf("probe", "python"))

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `malformed dependency metadata is an error`() {
    val output = """{"success":true,"actions":{"LINK":[{"name":"probe","version":"2.0","depends":"unknown"}]}}"""

    assertThat(parsePixiExternalNativeUpdates(output, "probe", setOf("probe")).isSuccess).isFalse()
  }

  @Test
  fun `an unchanged dependency with unknown metadata cannot authorize an update`() {
    val output = """{"success":true,"actions":{
      "LINK":[{"name":"probe","version":"2.0","depends":["existing-dependency >=1"]}],
      "UNLINK":[{"name":"probe","version":"1.0"}]
    }}"""

    val result = parsePixiExternalNativeUpdates(output, "probe", setOf("probe", "existing-dependency"))

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `an unchanged external dependency retains its virtual package requirements`() {
    val output = nativeUpdate("\"depends\":[\"existing-dependency >=1\"]")
    val installed = listOf(PixiInstalledCondaPackage("existing-dependency", "1.0", dependencies = listOf("__cuda >=99")))

    val result = parsePixiExternalNativeUpdates(output, "probe", setOf("probe", "existing-dependency"), installed)

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `known external dependency cycles do not hide the update`() {
    val output = nativeUpdate("\"depends\":[\"first >=1\"]")
    val installed = listOf(
      PixiInstalledCondaPackage("first", "1.0", dependencies = listOf("second >=1")),
      PixiInstalledCondaPackage("second", "1.0", dependencies = listOf("first >=1")),
    )

    val result = parsePixiExternalNativeUpdates(output, "probe", setOf("probe", "first", "second"), installed)

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).containsExactly(PixiNativePackage("probe", "2.0"))
  }

  @Test
  fun `optional constraints also validate unchanged external dependencies`() {
    val output = nativeUpdate("\"depends\":[],\"constrains\":[\"existing-dependency >=1\"]")
    val installed = listOf(PixiInstalledCondaPackage("existing-dependency", "1.0", dependencies = listOf("__cuda >=99")))

    val result = parsePixiExternalNativeUpdates(output, "probe", setOf("probe", "existing-dependency"), installed)

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `optional constraints do not require an absent package`() {
    val result = parsePixiExternalNativeUpdates(
      nativeUpdate("\"depends\":[],\"constrains\":[\"absent-dependency >=1\"]"), "probe", setOf("probe"),
    )

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).containsExactly(PixiNativePackage("probe", "2.0"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["version", "build", "source", "checksum", "unknown"])
  fun `active artifact drift cannot authorize an external update`(drift: String) {
    val expected = artifact()
    val observed = when (drift) {
      "version" -> artifact(version = "3.0")
      "build" -> artifact(build = "other")
      "source" -> artifact(source = "https://other.example.org/dependency-1.0-0.conda")
      "checksum" -> artifact(digest = "b".repeat(64))
      else -> null
    }
    val installed = listOf(PixiInstalledCondaPackage(
      "dependency", observed?.version ?: "1.0", dependencies = emptyList(), artifact = observed,
    ))
    val result = parsePixiExternalNativeUpdates(
      nativeUpdate("\"depends\":[],\"constrains\":[\"dependency >=1\"]"), "probe", setOf("probe", "dependency"),
      installed, mapOf("dependency" to expected),
    )

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `a missing active artifact cannot hide an optional constraint conflict`() {
    val result = parsePixiExternalNativeUpdates(
      nativeUpdate("\"depends\":[],\"constrains\":[\"dependency >=3\"]"), "probe", setOf("probe"),
      activeArtifacts = mapOf("dependency" to artifact()),
    )

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `an active artifact overwritten by pip cannot authorize an external update`() {
    val expected = artifact(source = "https://example.org/six-1.0-0.conda")
    val rawInventory = listOf(PixiInstalledCondaPackage(
      "six", "1.0", dependencies = emptyList(), artifact = expected,
    ))

    val result = parsePixiExternalNativeUpdates(
      nativeUpdate("\"depends\":[\"six >=1\"]"), "probe", setOf("probe"), rawInventory, mapOf("six" to expected),
    )

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `an external dependency overwritten by pip cannot authorize an external update`() {
    val rawInventory = listOf(PixiInstalledCondaPackage("six", "1.16", dependencies = emptyList()))

    val result = parsePixiExternalNativeUpdates(
      nativeUpdate("\"depends\":[\"six <1.17\"]"), "probe", setOf("probe"), rawInventory,
    )

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `an optional constraint cannot ignore an external package overwritten by pip`() {
    val rawInventory = listOf(PixiInstalledCondaPackage("six", "1.16", dependencies = emptyList()))

    val result = parsePixiExternalNativeUpdates(
      nativeUpdate("\"depends\":[],\"constrains\":[\"six <1.17\"]"), "probe", setOf("probe"), rawInventory,
    )

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `an absent optional package remains safe after an unrelated pip replacement`() {
    val rawInventory = listOf(PixiInstalledCondaPackage("six", "1.16", dependencies = emptyList()))

    val result = parsePixiExternalNativeUpdates(
      nativeUpdate("\"depends\":[],\"constrains\":[\"absent-dependency >=1\"]"), "probe", setOf("probe"), rawInventory,
    )

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).containsExactly(PixiNativePackage("probe", "2.0"))
  }

  @Test
  fun `an exact active artifact retains its proven virtual compatibility`() {
    val expected = artifact()
    val installed = listOf(PixiInstalledCondaPackage(
      "dependency", "1.0", dependencies = listOf("__cuda >=99"), artifact = expected,
    ))
    val result = parsePixiExternalNativeUpdates(
      nativeUpdate("\"depends\":[\"dependency >=1\"]"), "probe", setOf("probe", "dependency"),
      installed, mapOf("dependency" to expected),
    )

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).containsExactly(PixiNativePackage("probe", "2.0"))
  }

  @Test
  fun `an update without a transaction has no candidate`() {
    val result = parsePixiExternalNativeUpdates("""{"success":true,"message":"Already installed"}""", "probe", setOf("probe"))

    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `an unsatisfiable solve is an empty candidate list`() {
    val output = """{"success":false,"solver_problems":["The requested version conflicts with Python."],
      "log_history":[{"level":"error","message":"Could not solve for environment specs\nThe version conflicts with Python."},
                     {"level":"critical","message":"Could not solve for environment specs"}]}"""
    val process = EelProcessExecutionResult(1, output.toByteArray(), byteArrayOf())

    val transformed = PIXI_EXTERNAL_UPDATE_OUTPUT_TRANSFORMER(process)
    val parsed = parsePixiExternalNativeUpdates(output, "probe", setOf("probe"))

    assertThat(transformed.isSuccess).isTrue()
    assertThat(transformed.getOrThrow()).isEqualTo(output)
    assertThat(parsed.isSuccess).isTrue()
    assertThat(parsed.getOrThrow()).isEmpty()
  }

  @ParameterizedTest
  @ValueSource(strings = ["invalid", "{}", "[]", "{\"success\":false}", "{\"success\":true,\"actions\":{\"LINK\":[{}]}}"])
  fun `malformed and failed transactions are errors`(output: String) {
    assertThat(parsePixiExternalNativeUpdates(output, "probe", setOf("probe")).isSuccess).isFalse()
  }

  @ParameterizedTest
  @ValueSource(strings = ["Unauthorized", "Failed to download repodata", "Connection refused"])
  fun `a repository or process failure is not a successful empty update`(message: String) {
    val output = """{"success":false,"log_history":[{"level":"error","message":"$message"}]}"""
    val process = EelProcessExecutionResult(1, output.toByteArray(), message.toByteArray())

    assertThat(PIXI_EXTERNAL_UPDATE_OUTPUT_TRANSFORMER(process).isSuccess).isFalse()
  }

  @Test
  fun `a repository failure remains an error when the solver also fails`() {
    val output = """{"success":false,"solver_problems":["conflicting requests"],
      "log_history":[{"level":"error","message":"Failed to download repodata"}]}"""
    val process = EelProcessExecutionResult(1, output.toByteArray(), byteArrayOf())

    assertThat(PIXI_EXTERNAL_UPDATE_OUTPUT_TRANSFORMER(process).isSuccess).isFalse()
    assertThat(parsePixiExternalNativeUpdates(output, "probe", setOf("probe")).isSuccess).isFalse()
  }

  @Test
  fun `a killed process cannot turn a solver message into success`() {
    val output = """{"success":false,"solver_problems":["conflicting requests"]}"""
    val process = EelProcessExecutionResult(137, output.toByteArray(), byteArrayOf())

    assertThat(PIXI_EXTERNAL_UPDATE_OUTPUT_TRANSFORMER(process).isSuccess).isFalse()
  }

  private fun nativeUpdate(fields: String): String = """{"success":true,"actions":{
    "LINK":[{"name":"probe","version":"2.0",$fields}],"UNLINK":[{"name":"probe","version":"1.0"}]
  }}"""

  private fun artifact(
    version: String = "1.0", build: String = "0", source: String = "https://example.org/dependency-1.0-0.conda",
    digest: String = "a".repeat(64),
  ): PixiNativeArtifact = checkNotNull(createPixiNativeArtifact(version, build, source, digest, null))
}
