// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isSuccess
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Subsystems.PackagingRequirements
@Layers.Functional
@TestFor(issues = ["PY-91735"])
internal class PixiPackageListTest {
  @Test
  fun `native search reads records from every platform`() {
    val result = parsePixiNativePackageSearch("""
      {
        "noarch": [{"name":"typing_extensions","version":"4.15.0","build":"py_0"}],
        "osx-arm64": [{"name":"python","version":"3.13.0","depends":["openssl"]}]
      }
    """.trimIndent())
    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).containsExactly(
      PixiNativePackage("typing_extensions", "4.15.0"), PixiNativePackage("python", "3.13.0"),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["invalid", "[]", "{\"noarch\":[{\"name\":\"python\"}]}"])
  fun `native search rejects malformed output`(output: String) {
    assertThat(parsePixiNativePackageSearch(output).isSuccess).isFalse()
  }

  @Test
  fun `native search can return no packages`() {
    val result = parsePixiNativePackageSearch("{}")
    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).isEmpty()
  }

  @Test
  fun `a workspace package with a null version does not hide other packages`() {
    val result = parsePixiPackageList("""
      [
        {"name":"app","version":null,"kind":"pypi","source":"./packages/app"},
        {"name":"testpath","version":"0.6.0","kind":"pypi","index_url":"https://pypi.org/simple/"},
        {"name":"python","version":"3.15.1","kind":"conda"}
      ]
    """.trimIndent())
    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrThrow()).extracting("name", "version", "kind").containsExactly(
      tuple("app", null, "pypi"),
      tuple("testpath", "0.6.0", "pypi"),
      tuple("python", "3.15.1", "conda"),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["{ path = \"packages/app\", editable = true }", "{ git = \"https://example.org/app.git\", editable = true }"])
  fun `editable package status comes from the Pixi dependency specification`(specification: String) {
    val escaped = specification.replace("\"", "\\\"")
    val result = parsePixiPackageList("""[{"name":"app","version":null,"kind":"pypi","requested_spec":"$escaped"}]""")
    assertThat(result.getOrThrow()).singleElement().extracting { it.editable }.isEqualTo(true)
  }

  @ParameterizedTest
  @ValueSource(strings = ["{ path = \"packages/app\" }", "{ path = \"packages/app\", editable = false }", "\"==1.0\"", "invalid"])
  fun `a regular package is not marked editable`(specification: String) {
    val escaped = specification.replace("\"", "\\\"")
    val result = parsePixiPackageList("""[{"name":"app","version":"1.0","kind":"pypi","requested_spec":"$escaped"}]""")
    assertThat(result.getOrThrow()).singleElement().extracting { it.editable }.isEqualTo(false)
  }
}
