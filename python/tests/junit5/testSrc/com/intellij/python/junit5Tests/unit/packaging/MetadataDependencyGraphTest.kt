// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging

import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageMetadata
import com.jetbrains.python.packaging.management.metadataDependencyGraph
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MetadataDependencyGraphTest {

  @Test
  fun `reads the name out of a specifier, extras and a marker`() {
    val graph = metadataDependencyGraph(
      installed = listOf(pkg("httpx", "0.19.0"), pkg("certifi", "2024.2.2"), pkg("socksio", "1.0.0"), pkg("h2", "4.1.0")),
      metadata = mapOf(metadata("httpx", "certifi (>=2017.4.17)", """socksio ; extra == "socks"""", "h2[extra]>=3,<5")),
    )
    assertEquals(listOf("certifi", "socksio", "h2"), graph.single { it.name.name == "httpx" }.children.map { it.name.name })
  }

  @Test
  fun `matches a requirement spelled differently from the installed name`() {
    // `zope.interface`, `zope_interface` and `Twisted` are the same two names once PEP 503 normalization is applied,
    // which `PyPackageName` does for every node it holds
    val graph = metadataDependencyGraph(
      installed = listOf(pkg("zope.interface", "6.2"), pkg("Twisted", "24.3.0")),
      metadata = mapOf(metadata("twisted", "zope_interface (>=5)")),
    )
    assertEquals(listOf("zope-interface"), graph.single { it.name.name == "twisted" }.children.map { it.name.name })
  }

  @Test
  fun `drops a requirement that is not installed`() {
    // what makes the markers safe to ignore: an extra this environment did not ask for is not there to link to
    val graph = metadataDependencyGraph(
      installed = listOf(pkg("httpx", "0.19.0")),
      metadata = mapOf(metadata("httpx", """socksio ; extra == "socks"""", "certifi")),
    )
    assertTrue(graph.single().children.isEmpty()) { "Unexpected edge to a package that is not installed" }
  }

  private fun pkg(name: String, version: String) = PythonPackage(name, version, false)

  private fun metadata(name: String, vararg requiresDist: String) =
    PyPackageName.from(name) to PythonPackageMetadata(name = name, requiresDist = requiresDist.toList())
}
